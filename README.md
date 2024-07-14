# Cache Query Application

This Spring Boot application manages questions and their associated responses, utilizing caching and an external API for responses.

## Features

- **Query Handling:** Allows querying of questions and responses.
- **Caching:** Utilizes caching for faster response retrieval.
- **External API Integration:** Fetches responses from an external API when necessary.
- **Retry Mechanism:** Implements retry logic for robust external API calls.
- **Exception Handling:** Handles exceptions gracefully, ensuring reliability.

## Setup

### Requirements

- Java Development Kit (JDK) 8 or higher
- Maven build tool
- IDE (like IntelliJ IDEA or Eclipse)

### Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/alaeddine-hash/Cache_Query.git
   cd Cache_Query

Build the project:
mvn clean install

Run the application:
java -jar target/cache-query-app.jar

Configuration : 
The application is configured using application.properties file located in the src/main/resources folder.
The application is configured to use an in-memory cache by default. To use a different cache implementation,


external.api.url=http://example.com/api/query


Endpoint: Use the following endpoint to query responses:

POST /questions/query

Example request body:
{
  "query": "What is your name?",
  "language": "english"
}

Example response:

{
  "response": "My name is ChatGPT."
}
