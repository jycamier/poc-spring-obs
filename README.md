# POC — traces, logs et contexte e-commerce avec Micrometer Tracing

Trois microservices Spring Boot 4.1 (`ms-cart`, `ms-catalog`, `ms-user`) instrumentés uniquement avec le standard :
Micrometer Tracing + bridge OpenTelemetry, export OTLP vers un collector, traces dans Jaeger, logs dans Loki,
corrélation dans Grafana.

```
k6 ──JWT──► ms-cart ──► ms-user
                  └───► ms-catalog
   customer.id : lu dans le JWT validé (point d'entrée)
   cart.id     : posé par ms-cart, propriétaire du panier
   propagés en aval par traceparent + baggage

ms-* ──OTLP──► otel-collector ──► Jaeger (traces)
                              └─► Loki   (logs)      ◄── Grafana (corrélation logs ↔ traces)
```

## Lancer

```bash
./mvnw package jib:dockerBuild     # construit poc/ms-cart, poc/ms-catalog, poc/ms-user dans le Docker local
docker compose up -d
docker compose run --rm k6         # scénarios de navigation (1 min)
```

Appels à la main :

```bash
TOKEN=$(./jwt/mint.sh C-1001)                                          # JWT signé, valide jusqu'en 2030
curl -H "Authorization: Bearer $TOKEN" localhost:8080/carts/cart-42       # 200
curl -H "Authorization: Bearer $TOKEN" localhost:8080/carts/cart-broken   # 502 : ms-catalog renvoie 500
curl -H "Authorization: Bearer $TOKEN" localhost:8080/carts/cart-slow     # 504 : ms-catalog dépasse le read-timeout (1 s)
curl -H "Authorization: Bearer $TOKEN" localhost:8080/carts/cart-bug      # 502 : exception non gérée (bug) dans ms-catalog
curl localhost:8080/carts/cart-42                                         # 401 : pas de token
curl -H 'x-customer-id: C-1001' localhost:8080/carts/cart-42             # 401 : un header ne suffit plus
```

Les JWT sont signés avec `jwt/poc-private.pem` (clé de test, générée pour le POC) et validés par les services avec
`security/src/main/resources/jwt/poc-public.pem`. `k6/tokens.json` contient des JWT pour `C-1001` à `C-1010`.

Les premiers appels après le démarrage peuvent renvoyer un 504 : JVMs froides, timeouts courts
(`ms-user` 500 ms, `ms-catalog` 1 s).

| UI | URL |
|---|---|
| Grafana — dashboard « Parcours client » (page d'accueil) | http://localhost:3000 |
| Grafana — Explore (requêtes libres) | http://localhost:3000/explore |
| Jaeger | http://localhost:16686 |

## Ce qu'il faut regarder

**Le parcours d'un client.** La page d'accueil de Grafana est le dashboard « Parcours client » : saisir un
`customer_id` affiche ses traces (ID cliquable) et ses logs de tous les services, service et niveau en tête de ligne.

**Depuis un log vers sa trace.** Grafana → Explore → Loki : `{service_name="ms-catalog"} | detected_level="error"`.
Ouvrir la ligne : `trace_id`, `customer_id`, `cart_id`, `exception_type`, `exception_stacktrace` sont des champs.
Le lien **Open trace** ouvre la trace Jaeger.

**Depuis une trace vers ses logs.** Grafana → Explore → Jaeger, chercher une trace de `ms-cart` avec le tag
`cart.id=cart-broken`. Sur un span, **Logs for this span** affiche les logs de toute la trace, tous services confondus.

**Le contexte e-commerce partout, posé par celui qui le connaît.**
- `customer.id` vient du **JWT validé** par Spring Security (claim `customer_id`), jamais d'un header : un client ne
  peut pas se faire passer pour un autre dans les logs.
- `cart.id` est posé par **ms-cart**, propriétaire du panier, depuis sa route `/carts/{cartId}`. Les logs écrits
  avant (et les fiches produit vues hors panier) n'ont pas de `cart_id` : c'est normal.
- `ms-catalog` et `ms-user` ne contiennent aucune ligne d'observabilité, et pourtant leurs logs et leurs spans
  portent les deux : ils arrivent par le header W3C `baggage`.

Dans Jaeger, chercher par tag `customer.id=C-1001` retrouve tout le parcours d'un client.

**Un 500 en aval = des logs exploitables.** Pour `cart-broken` : le span `ms-cart` en erreur, le log ERROR de
`ms-catalog` avec la vraie cause (`Price list EU-2026 has no entry…`) et le log ERROR de `ms-cart` avec le corps
de la réponse, tous reliés par le même `trace_id`.

## Où est quoi

| Fichier | Rôle |
|---|---|
| `observability/` | Ce qu'une lib commune (type ms-common) apporterait à tous les services |
| `observability/…/observability.yml` | Toute la config : sampling, baggage, endpoints OTLP. Importée par chaque service |
| `observability/…/logback-spring.xml` | Console + appender OpenTelemetry (les logs partent en OTLP avec trace_id) |
| `observability/…/ObservabilityAutoConfiguration.java` | Branche l'appender Logback sur le SDK OTel, recopie le baggage sur les spans |
| `observability/…/BaggageServerSpanTagger.java` | Tague le span serveur avec le baggage reçu de l'amont |
| `security/` | Validation JWT (resource server) partagée, JWT facultatif par défaut |
| `security/…/security.yml` | Clé publique de validation, `principal-claim-name: customer_id` |
| `security/…/CustomerContextFilter.java` | Met dans le baggage le client du JWT validé (seule source de `customer.id`) |
| `ms-cart/…/CartContextInterceptor.java` | ms-cart pose `cart.id` depuis sa route, jusqu'à la fin de la requête (logs d'erreur compris) |
| `ms-cart/…/CartWebConfiguration.java` | `/carts/**` exige un JWT ; enregistre l'intercepteur |
| `jwt/mint.sh`, `jwt/poc-private.pem` | Fabrique des JWT de test signés RS256 |
| `observability/…/UnhandledExceptionResolver.java` | Logue les exceptions non gérées dans le contexte de la trace (sinon Tomcat les logue sans trace_id) |
| `observability/…/ServerErrorRecorder.java` | Met le span serveur en erreur quand Spring MVC a lui-même produit un 5xx |
| `ms-cart/…/UserClient.java`, `CatalogClient.java` | Clients HTTP déclaratifs (`@HttpExchange`), un groupe par MS via `@ImportHttpServices` |
| `ms-cart/…/application.yml` | `spring.http.serviceclient.<groupe>.*` : base-url et timeouts par MS |
| `ms-cart/…/DownstreamErrorHandler.java` | Les erreurs en aval sont loguées une fois, avec l'exception, et renvoyées en ProblemDetail (502/504) |
| `otel-collector.yaml` | Reçoit OTLP, traces → Jaeger, logs → Loki (OTLP natif) |
| `grafana/dashboards/customer-journey.json` | Dashboard « Parcours client » : traces + logs d'un client |
| `grafana/datasources.yaml` | Liens log → trace (derived field sur `trace_id`) et trace → logs |
| `k6/navigation.js`, `k6/tokens.json` | Navigation anonyme, client connecté avec JWT (fiches produit, compte, panier), paniers en erreur et lents, accès sans token refusés |

## Ce que Spring Boot ne fait pas tout seul

Ces points ont été vérifiés en faisant tourner le POC.

1. **L'appender Logback → OpenTelemetry n'est pas fourni.** Boot configure l'export OTLP des logs
   (`management.opentelemetry.logging.export.otlp.endpoint`), mais il faut ajouter
   `opentelemetry-logback-appender-1.0` et l'installer sur le SDK (`ObservabilityAutoConfiguration`).
2. **`management.tracing.baggage.tag-fields` ne recopie pas le baggage sur tous les spans.** Seul le span courant
   au moment où le baggage est *créé* est tagué. Pour les autres : `BaggageTaggingSpanProcessor` (spans clients) et
   `BaggageServerSpanTagger` pour le span serveur (le baggage reçu n'est pas encore dans le contexte quand le span
   serveur démarre). Pour le baggage *créé* localement (`customer.id` depuis le JWT, `cart.id` dans ms-cart),
   `tag-fields` suffit.
3. **Un 500 produit par Spring MVC lui-même (`ResponseStatusException`, `@ExceptionHandler`) ne met pas le span
   serveur en erreur** : l'exception résolue n'est pas transmise à l'observation, et le span ne passe en erreur que
   via `Observation.error()` (pas au `stop()`). `ServerErrorRecorder` mémorise l'exception (resolver) et la reporte
   quand la réponse finale est un 5xx (filtre dans le scope de l'observation). Les 4xx restent sans erreur.
4. **Une exception non gérée est loguée par Tomcat hors du contexte** : sans `trace_id`, `customer_id`, `cart_id`,
   introuvable depuis la trace. `UnhandledExceptionResolver`, dernier resolver, la logue encore dans le scope de la
   requête et répond 500.
5. **Spring Security ajoute un span par phase de sa chaîne de filtres** à chaque trace : coupé par
   `SecurityObservationSettings.noObservations()` pour garder des traces lisibles.
6. **Jaeger ≥ 2.21 ne sert plus l'API HTTP legacy** utilisée par la datasource Jaeger de Grafana 13 :
   Jaeger est figé en 2.20.0.

