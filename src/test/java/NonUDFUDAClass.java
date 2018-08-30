public class NonUDFUDAClass {


    public static String safeMethod(){
        Runtime.getRuntime().availableProcessors();
        Class a = Compiler.class;
        return "I'm ok to reference blacklisted method";
    }
}
