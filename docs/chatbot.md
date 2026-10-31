# Asistente virtual (chatbot RAG)

## Flujo
Pregunta → validación (Bean Validation) → límite de mensajes (Redis) → candado por conversación (Redis)
→ historial reciente → búsqueda vectorial (Spring AI `VectorStore`, MongoDB Atlas Vector Search, filtrada por rol)
→ prompt con el contexto marcado como no confiable → modelo (Spring AI `ChatClient`, Groq)
→ se guarda el intercambio completo → respuesta con fuentes.
Si el proveedor falla no se guarda nada: nunca queda una pregunta sin respuesta.

## Configuración
Secretos (nunca en el repositorio; en dev van en `application-dev.properties`, en producción en variables de entorno):

| Propiedad | Variable de entorno |
|---|---|
| `spring.ai.openai.chat.api-key` | `GROQ_API_KEY` |
| `spring.ai.openai.embedding.api-key` | `EMBEDDINGS_API_KEY` |

Ambas son obligatorias al arrancar. No definas `spring.ai.openai.api-key`: si faltara la específica de embeddings,
la clave de Groq se enviaría al otro proveedor.

Propiedades principales (valores por defecto en `application.properties`):

| Propiedad | Defecto | Efecto |
|---|---|---|
| `spring.ai.openai.chat.options.model` | `openai/gpt-oss-120b` | Modelo de Groq |
| `spring.ai.openai.embedding.options.model` | `voyage-4-lite` | Modelo de embeddings |
| `chatbot.rag.dimensions` | 1024 | Debe coincidir con el índice vectorial |
| `chatbot.rag.min-score` | 0.70 | Umbral de relevancia (calibrar) |
| `chatbot.limits.max-message-length` | 1000 | Longitud máxima del mensaje |
| `chatbot.limits.messages-per-minute` / `messages-per-day` | 8 / 120 | Límite por usuario (Redis) |
| `chatbot.conversation.retention-days` | 90 | Retención de conversaciones inactivas |
| `chatbot.ingestion.directory` | `knowledge` | Carpeta de documentos |

Cambiar el modelo de embeddings o la dimensión exige recrear el índice y reingerir con `force=true`.

## Índice vectorial (Atlas)
Definición en `atlas/knowledgeVectors-vector-index.json`. Créalo en Atlas Search → Vector Search → JSON Editor,
colección `knowledgeVectors`, nombre `knowledgeVectors_vector_index`. Verifica con `GET /admin/knowledge/status`
(`queryable: true`). Un índice inexistente o en construcción puede devolver cero resultados sin error.

## Corpus e ingesta
- Fuentes permitidas: `knowledge/all|trainer|admin/` (la carpeta define la audiencia). No se aceptan rutas arbitrarias ni se leen las plantillas del sitio. Los documentos que dejen de estar en esa carpeta se retiran en la siguiente ingesta.
- Formatos: `.md`, `.txt`, `.html`, `.pdf` (sin OCR ni DOCX). Máximo 5 MB por archivo.
- Ejecución (rol ADMIN, con token CSRF): `POST /admin/knowledge/ingest` (`?force=true` reindexa todo).
- Es idempotente: un documento sin cambios no se reembebe. Los documentos que desaparecen de las fuentes se retiran.
- Los precios de los planes están en la colección `plans`, no en documentos: el asistente no los conoce a menos
  que se incorporen al corpus.

## Operación
- `GET /admin/knowledge/status`: conteos y estado del índice.
- `GET /admin/knowledge/documents`: documentos, estado y último error.
- `GET /admin/knowledge/search?query=...`: fragmentos con su puntaje, para calibrar `min-score`.

## Seguridad
- `/api/chat/**`: roles USER, TRAINER y ADMIN. `/admin/knowledge/**`: solo ADMIN.
- La identidad sale de la sesión; ningún identificador de usuario viaja desde el navegador.
- CSRF activo (cabecera `X-XSRF-TOKEN`). Una conversación ajena responde 404.
- Audiencia por rol aplicada en el servidor al buscar. Los documentos recuperados se tratan como contenido no confiable.
- Límites: tamaño del cuerpo, longitud del mensaje, mensajes por minuto y día, conversaciones por usuario, mensajes por conversación.

## Datos y retención
- Se guarda por usuario: texto de preguntas y respuestas, fechas y fuentes citadas (`chatConversations`).
- Se eliminan: a demanda por el usuario, automáticamente tras `retention-days` sin actividad (tarea diaria 03:30)
  y al eliminar la cuenta del usuario.
- Al LLM y al proveedor de embeddings se envía el texto de la consulta y del contexto; nunca nombre, correo ni datos de la cuenta.
- Los registros no incluyen mensajes, respuestas, prompts ni claves. Mantén en `false`
  `spring.ai.chat.observations.log-prompt` y `log-completion`.

## API
| Método y ruta | Descripción |
|---|---|
| `POST /api/chat/messages` | `{message, conversationId?}` → respuesta y fuentes |
| `POST /api/chat/conversations` | Crea una conversación vacía (201) |
| `GET /api/chat/conversations?page&size` | Conversaciones del usuario |
| `GET /api/chat/conversations/{id}/messages?limit` | Mensajes de una conversación |
| `DELETE /api/chat/conversations/{id}` | Elimina la conversación (204) |

Errores: 422 validación, 404 conversación inexistente, 409 ocupada/llena, 413 cuerpo grande,
429 límite (con `Retry-After`), 502/503 fallo del proveedor.

## Limitaciones conocidas
- La cita de fuentes depende de que el modelo cumpla la línea `FUENTES:`; si no, se devuelven los documentos consultados.
- El troceado es por tokens y sin solapamiento.
- La ingesta es síncrona (pensada para un corpus pequeño).
- Los límites de mensajes son de ventana fija: pueden permitir una ráfaga en el cambio de minuto.
- Si el proveedor de embeddings limita las solicitudes, la ingesta se detiene y deja el resto como `PENDING`; al volver a ejecutarla se omite lo ya procesado. Las pausas (`chatbot.ingestion.min-interval-between-requests`) pueden hacer que una corrida tarde varios minutos; el servidor la termina aunque cierres la pestaña.