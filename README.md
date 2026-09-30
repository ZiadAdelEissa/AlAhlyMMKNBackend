# MMKN Backend

English service dictionary, bill inquiry, bill details and demo payment receipt. Spring Boot, in-memory demo data, no authentication and no database setup.

## Run

Requires JDK 17 or later. Verified locally with JDK 26.0.2.1 and Maven 3.9.11.
Spring Boot 4.0.8 supports Java 17–26; see the [official system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html).

```powershell
.\mvnw.cmd spring-boot:run
```

Default URL: `http://localhost:8080/api/v1/services`. Set `PORT` to change the port.

## Verify and package

```powershell
.\mvnw.cmd verify
java -jar target/mmkn-backend-0.0.1-SNAPSHOT.jar
```

The first Maven Wrapper run needs internet access to download Maven and dependencies.

Frontend contract and examples: [docs/API.md](docs/API.md). Import [docs/openapi.yaml](docs/openapi.yaml) into an API client.

Open `http://localhost:8080/` for the working preview. Select a service and try subscriber `1024750891`: a successful inquiry opens Bill details automatically with a five-minute countdown. Demo payment and a success receipt are available. All providers, subscribers, amounts and availability values are demo fixtures. There are no real payments or user accounts.

Frontend handoff: [docs/API.md](docs/API.md). Separate Arabic walkthrough for the backend owner: [docs/PROJECT-GUIDE-AR.md](docs/PROJECT-GUIDE-AR.md).

Import `docs/MMKN.postman_collection.json` into Postman for API checks. Quotes, receipts and duplicate-payment protection are stored in memory and reset on server restart. No real payment is made. Service IDs are integers.

Every service includes `iconUrl`, a relative URL to its public SVG image under `/icons/`. The frontend should resolve it against the backend origin. Complete object/field type tables are included in both the frontend API guide and the separate Arabic project guide.

Validation report and Postman instructions: [docs/TEST-RESULTS.md](docs/TEST-RESULTS.md). Restart the demo server before running the entire collection, since payment changes bill state.
