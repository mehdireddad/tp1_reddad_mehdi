package ma.emsi.reddad.tp1_reddad_mehdi.llm;

public class RequeteException extends Exception {
    /**
     * Statut de la réponse du LLM.
     */
    private int status;
    private String requeteJson;

    public RequeteException(int status) {
        this.status = status;
    }

    public RequeteException(String message) {
        super(message);
    }

    public RequeteException(String message, String requeteJson) {
        super(message);
        this.requeteJson = requeteJson;
    }

    public RequeteException() {
    }

    public int getStatus() {
        return status;
    }

    public String getRequeteJson() {
        return requeteJson;
    }
}
