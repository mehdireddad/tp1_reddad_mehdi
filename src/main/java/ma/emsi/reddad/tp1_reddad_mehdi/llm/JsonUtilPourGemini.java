package ma.emsi.reddad.tp1_reddad_mehdi.llm;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.json.stream.JsonGenerator;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Classe pour gérer le JSON des requêtes à l'API de Gemini.
 * Contient l'état JSON de la conversation et des méthodes pour manipuler le JSON.
 */
@Dependent
public class JsonUtilPourGemini implements Serializable {

    private String systemRole; // = "helpful assistant";
    /**
     * Pour ajouter une nouvelle valeur à la fin du tableau JSON "messages" dans le document JSON de la requête.
     * Le "-" final indique que la valeur sera ajoutée à la fin du tableau.
     */
    private final JsonPointer pointer = Json.createPointer(("/contents/-"));

    /**
     * Requête JSON, à partir du prompt de l'utilisateur.
     */
    private JsonObject requeteJson;
    private String texteRequeteJson;

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    /**
     * Pour envoyer une requête à l'API de Gemini.
     */
    @Inject
    private LlmClientPourGemini geminiClient;

    /**
     * Envoi une requête à l'API de Gemini.
     * Format du document JSON envoyé dans la requête vers l'API :
     * {
     *     "contents": [
     *         {
     *             "role": "user",
     *             "parts": [
     *                 {
     *                     "text": "Capitale de la France ?"
     *                 }
     *             ]
     *         },
     *         {
     *             "role": "model",
     *             "parts": [
     *                 {
     *                     "text": "Paris est la capitale de la France."
     *                 }
     *             ]
     *         },
     *         ...
     *     ]
     * }
     * * @param question question posée par l'utilisateur
     *
     * @return la réponse de l'API, sous la forme d'un texte simple (pas JSON).
     * @throws RequeteException exception lancée dans le cas où la requête a été rejetée par l'API.
     */
    public LlmInteraction envoyerRequete(String question) throws RequeteException {
        String requestBody;

        // 💡 CORRECTION : Sauvegarder l'état précédent de la requête
        JsonObject jsonAvantAjout = this.requeteJson;

        if (this.requeteJson == null) {
            // Si c'est la première question, crée la requête JSON avec le rôle système.
            requestBody = creerRequeteJson(this.systemRole, question);
        } else {
            // Ajout de la question.
            requestBody = ajouteQuestionDansJsonRequete(question);
        }
        // À ce stade, this.requeteJson contient la nouvelle question "user"

        Entity<String> entity = Entity.entity(requestBody, MediaType.APPLICATION_JSON_TYPE);
        // Pour afficher la requête JSON dans la page JSF
        this.texteRequeteJson = prettyPrinting(requeteJson);

        // Envoi la requête par l'intermédiaire du client de l'API de Gemini.
        try (Response response = geminiClient.envoyerRequete(entity)) {
            // Entité incluse dans la réponse (texte au format JSON qui englobe la réponse à la question)
            String texteReponseJson = response.readEntity(String.class);
            if (response.getStatus() == 200) {
                // Succès : extractReponse mettra à jour this.requeteJson avec la réponse "model"
                return new LlmInteraction(this.texteRequeteJson, texteReponseJson, extractReponse(texteReponseJson));
            } else {
                // 💡 CORRECTION : Erreur ! Annuler l'ajout de la question "user"
                this.requeteJson = jsonAvantAjout;

                // Pour voir la requête JSON s'il y a eu un problème.
                JsonObject objet = Json.createReader(new StringReader(requestBody)).readObject();
                throw new RequeteException(response.getStatus() + " : " + response.getStatusInfo(), prettyPrinting(objet));
            }
        } catch (Exception e) {
            // 💡 CORRECTION : Gérer aussi les exceptions de communication (ex: réseau)
            this.requeteJson = jsonAvantAjout; // Annuler l'ajout

            if (e instanceof RequeteException) {
                throw (RequeteException) e; // Re-lancer si c'est déjà notre exception
            }
            // Encapsuler les autres exceptions (ex:
            // jakarta.ws.rs.ProcessingException)
            throw new RequeteException("Erreur de communication : " + e.getMessage(), this.texteRequeteJson);
        }
    }

    /**
     * Crée une requête JSON pour envoyer à l'API de Gemini.
     * Il y a le rôle du système et la question de l'utilisateur.
     * Format du document JSON envoyé dans la requête vers l'API :
     * {
     *    "system_instruction": {
     *      "parts": [ {"text": "helpful assistant"} ]
     *    },
     *    "contents": [
     *        { "role": "user",
     *          "parts": [ { "text": "Capitale de la France ?" } ]
     *        }
     *    ]
     * }
     *
     * @param systemRole le rôle du système. Par exemple, "helpful assistant".
     * @param question question posée par l'utilisateur.
     * @return le texte du document JSON de la requête.
     */
    private String creerRequeteJson(String systemRole, String question) {
        // Création de l'objet "system_instruction"
        JsonArray systemInstructionParts = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("text", systemRole))
                .build();
        JsonObject systemInstruction = Json.createObjectBuilder()
                .add("parts", systemInstructionParts)
                .build();
        // Création de l'objet "contents"
        JsonArray userContentParts = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("text", question))
                .build();
        JsonObject userContent = Json.createObjectBuilder()
                .add("role", "user")
                .add("parts", userContentParts)
                .build();
        JsonArray contents = Json.createArrayBuilder()
                .add(userContent)
                .build();
        // Création de l'objet racine
        JsonObject rootJson = Json.createObjectBuilder()
                .add("system_instruction", systemInstruction)
                .add("contents", contents)
                .build();
        this.requeteJson = rootJson;

        return rootJson.toString();
    }

    /**
     * Modifie le JSON de la requete pour ajouter le JsonObject lié à la nouvelle question dans messagesJson.
     * Il faut ajouter au tableau JSON.
     *
     * @param nouvelleQuestion question posée par l'utilsateur.
     * @return le texte du document JSON de la requête.
     */
    private String ajouteQuestionDansJsonRequete(String nouvelleQuestion) {
        // Crée le nouveau JsonObject qui correspond à la nouvelle question
        JsonObject nouveauMessageJson = Json.createObjectBuilder()
                .add("text", nouvelleQuestion)
                .build();
        // Crée le JsonArray parts
        JsonObjectBuilder newPartBuilder = Json.createObjectBuilder()
                .add("role", "user")
                .add("parts", Json.createArrayBuilder()
                        .add(nouveauMessageJson)
                        .build());
        // Ajoute ce nouveau JsonObjet dans this.requeteJson
        this.requeteJson = this.pointer.add(this.requeteJson, newPartBuilder.build());
        // La requête sous la forme d'une String avec mise en forme (passage à la ligne et indentation).
        this.texteRequeteJson = prettyPrinting(requeteJson);
        return this.requeteJson.toString();
    }



    /**
     * Retourne le texte formaté du document JSON pour un affichage plus agréable.
     *
     * @param jsonObject l'objet JSON dont on veut une forme formatée.
     * @return la forme formatée
     */
    private String prettyPrinting(JsonObject jsonObject) {
        Map<String, Boolean> config = new HashMap<>();
        config.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory writerFactory = Json.createWriterFactory(config);
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = writerFactory.createWriter(stringWriter)) {
            jsonWriter.write(jsonObject);
        }
        return stringWriter.toString();
    }

    /**
     * Extrait la réponse de l'API et ajoute la réponse à this.jsonRequete pour garder la conversation dans
     * la prochaine requête.
     * @param json le document JSON de la réponse.
     * @return juste la valeur de content qui contient la réponse à la question.
     */
    private String extractReponse(String json) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(json))) {
            JsonObject jsonObject = jsonReader.readObject();
            JsonObject messageReponse = jsonObject
                    .getJsonArray("candidates")
                    .getJsonObject(0)
                    .getJsonObject("content");
            // Ajoute l'objet JSON de la réponse de l'API au JSON de la prochaine requête
            this.requeteJson = this.pointer.add(this.requeteJson, messageReponse);
            // Extrait seulement le texte de la réponse
            return messageReponse.getJsonArray("parts").getJsonObject(0).getString("text");
        }
    }

}
