# Fitness Life

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-wrapper-C71A36?logo=apachemaven)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/license-not%20specified-lightgrey)](#licencia)

Aplicación web para la gestión de un gimnasio. Fitness Life reúne la administración de usuarios, membresías, pagos, asistencia y clases en una aplicación Spring Boot, con herramientas de análisis de riesgo de cancelación y optimización de acciones de retención.

## Características

- Gestión de usuarios, roles, planes, pagos y entrenamientos funcionales.
- Registro de asistencia mediante código QR y vista de quiosco.
- Panel administrativo con métricas y predicciones de abandono.
- Optimización de campañas de retención por segmento de riesgo, conectada a un servicio externo FastAPI/PuLP.
- Asistente virtual con recuperación de documentos (RAG), Spring AI y MongoDB Atlas Vector Search.
- Interfaz web renderizada con Thymeleaf y protección de rutas mediante Spring Security.

## Tecnologías

- Java 21 y Spring Boot 3.5.10
- Spring MVC, Thymeleaf, Spring Security y Bean Validation
- MongoDB, Redis y Spring Session
- Spring AI, MongoDB Atlas Vector Search y proveedores compatibles con la API de OpenAI
- Maven Wrapper
- Servicio de optimización externo: FastAPI, PuLP y CBC (el código Python compartido se ejecuta como servicio independiente)

## Requisitos previos

- JDK 21.
- MongoDB accesible desde la aplicación (MongoDB Atlas para el almacén vectorial del asistente).
- Redis accesible para sesiones y límites del chatbot.
- Credenciales de ePayco y SMTP si se habilitan pagos y correos.
- Credenciales del proveedor de chat y embeddings si se utiliza el asistente virtual.
- Servicio de optimización disponible si se utiliza la función de optimización de retención.
- Git para clonar el repositorio. No es necesario instalar Maven: el proyecto incluye Maven Wrapper.

## Instalación y configuración

1. Clona el repositorio y entra en la carpeta:

   ```bash
   git clone <URL_DEL_REPOSITORIO>
   cd fitnesslife-java21
   ```

2. Configura las variables de entorno. Spring Boot no carga archivos `.env` automáticamente; puedes usar el siguiente archivo como referencia y exportar sus valores en tu shell o configurarlos en tu IDE/plataforma de despliegue.

   Ejemplo de `.env.example` (usa valores propios y no guardes secretos reales en Git):

   ```env
   SPRING_PROFILES_ACTIVE=prod
   MONGODB_URI=mongodb+srv://<USUARIO>:<PASSWORD>@<CLUSTER>/<BASE_DE_DATOS>
   REDIS_HOST=localhost
   REDIS_PORT=6379
   REDIS_PASSWORD=
   REDIS_SSL_ENABLED=false

   EPAYCO_CUSTOMER_ID=<TU_CUSTOMER_ID>
   EPAYCO_PRIVATE_KEY=<TU_PRIVATE_KEY>
   EPAYCO_PUBLIC_KEY=<TU_PUBLIC_KEY>
   EPAYCO_TEST_MODE=true

   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=<TU_CORREO>
   MAIL_PASSWORD=<TU_PASSWORD_DE_APLICACION>

   # Integraciones usadas por el chatbot RAG
   SPRING_AI_OPENAI_CHAT_API_KEY=<TU_API_KEY_DE_CHAT>
   SPRING_AI_OPENAI_CHAT_BASE_URL=https://api.groq.com/openai
   SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=openai/gpt-oss-120b
   SPRING_AI_OPENAI_EMBEDDING_API_KEY=<TU_API_KEY_DE_EMBEDDINGS>
   SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://ai.mongodb.com
   SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL=voyage-4-lite

   # URL completa del endpoint POST /optimize-retention del microservicio
   OPTIMIZER_API_URL=http://localhost:8000/optimize-retention
   APP_BASE_URL=http://localhost:8080
   ```

   MongoDB, Redis, las claves del proveedor de IA y `OPTIMIZER_API_URL` deben corresponder a servicios accesibles. Para ejecutar sin alguna integración, configura el perfil y las credenciales según las funciones que vayas a habilitar. En desarrollo, el proyecto usa el perfil `dev` por defecto; configura sus propiedades locales en `application-dev.properties` (archivo ignorado por Git) y no compartas credenciales.

3. Compila el proyecto:

   ```bash
   ./mvnw clean package
   ```

   En Windows PowerShell usa `mvnw.cmd clean package`.

## Ejecución

### Desarrollo

Con las dependencias externas configuradas y accesibles:

```bash
./mvnw spring-boot:run
```

Abre [http://localhost:8080](http://localhost:8080). La aplicación sirve la interfaz web y redirige a autenticación cuando la ruta lo requiere.

### Producción

Define las variables de entorno del entorno de despliegue y ejecuta el artefacto generado:

```bash
java -jar target/gym-0.0.1.jar
```

Para desarrollo local, el perfil activo por defecto es `dev`. Para producción, define `SPRING_PROFILES_ACTIVE=prod` antes de iniciar la aplicación.

### Servicio de optimización de retención

La aplicación Java delega la resolución del modelo a un servicio FastAPI independiente. Configura `OPTIMIZER_API_URL` con la URL completa del endpoint `POST /optimize-retention`. El servicio expone `GET /health` para comprobar disponibilidad; por ejemplo:

```bash
curl http://localhost:8000/health
```

El fuente del servicio Python/PuLP se comparte por separado y no forma parte de este repositorio; por eso sus comandos de instalación y despliegue dependen del archivo de dependencias y del módulo ASGI con el que se publique.

## Estructura del proyecto

```text
fitnesslife-gym/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/fitnesslife/gym/
│       │       ├── config/          # Configuraciones de seguridad, Spring y beans
│       │       ├── controller/      # Controladores web y endpoints REST
│       │       ├── dto/             # Objetos de transferencia de datos
│       │       ├── enums/           # Enumeraciones y constantes del dominio
│       │       ├── event/           # Eventos del sistema y listeners
│       │       ├── exception/       # Manejo global de excepciones
│       │       ├── mapper/          # Conversores entre entidades y DTOs
│       │       ├── migration/       # Scripts o lógica de migración de datos
│       │       ├── model/           # Entidades JPA y modelos de dominio
│       │       ├── repository/      # Repositorios de acceso a datos
│       │       ├── scheduler/       # Tareas programadas (cron jobs)
│       │       ├── service/         # Lógica de negocio
│       │       ├── tool/            # Herramientas de IA (Tool Calling)
│       │       ├── validation/      # Validadores personalizados
│       │       └── GymApplication.java
│       └── resources/
│           ├── model/               # Recursos de modelos (ej. ML/IA)
│           ├── static/              # Recursos estáticos (css/, img/, js/, favicon.ico)
│           ├── templates/           # Vistas Thymeleaf (admin, auth, client, trainer, etc.)
│           ├── application.properties      # Configuración base
│           ├── application-dev.properties  # Perfil de desarrollo
│           ├── application-stg.properties  # Perfil de pruebas (staging)
│           ├── application-prod.properties # Perfil de producción
│           └── banner.txt           # Banner de inicio de Spring Boot
├── docs/                            # Documentación técnica
├── knowledge/                       # Documentos de conocimiento (RAG) para el chatbot
├── qrcode/                          # Códigos QR generados
├── uploads/                         # Archivos cargados por la aplicación
└── pom.xml                          # Dependencias y configuración Maven
                            Dependencias y configuración Maven
```
