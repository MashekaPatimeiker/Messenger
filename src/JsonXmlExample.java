public class JsonXmlExample {
    public static String getJsonResponse() {
        return "{\n" +
                "  \"status\": \"success\",\n" +
                "  \"data\": {\n" +
                "    \"id\": 123,\n" +
                "    \"name\": \"John Doe\",\n" +
                "    \"email\": \"john@example.com\",\n" +
                "    \"roles\": [\"user\", \"admin\"]\n" +
                "  }\n" +
                "}";
    }

    public static String getXmlResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<response>\n" +
                "  <status>success</status>\n" +
                "  <data>\n" +
                "    <id>123</id>\n" +
                "    <name>John Doe</name>\n" +
                "    <email>john@example.com</email>\n" +
                "    <roles>\n" +
                "      <role>user</role>\n" +
                "      <role>admin</role>\n" +
                "    </roles>\n" +
                "  </data>\n" +
                "</response>";
    }
}