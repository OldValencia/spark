package to.sparkapp.app.browser.webview;

public interface WebviewBindCallback {

    /**
     * @param  jsonArgs A JSON string containing an array of arguments.
     * 
     * @return          A JSON string to be deserialized in the Webview.
     */
    public String apply(String jsonArgs) throws Throwable;

}
