public class Main {
    public static void main(String[] args) {

        String uri = System.getenv("MONGODB_URI");

        if (uri == null || uri.isBlank()) {
            System.out.println("❌ MONGODB_URI NOT FOUND");
        } else {
            System.out.println("✅ MONGODB_URI FOUND");
        }
    }
}
