import com.maxtropy.javac.CqlType;
import com.maxtropy.javac.UDA;
import com.maxtropy.javac.UDF;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Sample {

    public static String method() {
        return "method";
    }

    @UDF(name = "overrideMethodName", isReplaceOld = false, keySpace = "kingfisher")
    @CqlType(cqlType = "list<cint>")
    private static String  sampleMethod(@CqlType(cqlType = "text") String someparameters){
        System.out.println("hello world");
//        Class a = Compiler.class;
        return "string" + method();
    }

    /*public static java.lang.String sampleMethod_v1_cqlStr() {
        return "CREATE IF NOT EXISTS FUNCTION sampleMethod_v1( text someparameters ) RETURNS NULL ON NULL INPUT RETURNS list<cint> LANGUAGE java AS $$ {\n    System.out.println(\"hello world\");\n    return new ArrayList<>(1);\n} $$";
    }*/

    @UDF(isReplaceOld = true,keySpace = "kingfisher")
    @CqlType(cqlType = "text")
    private static String finalFunc(@CqlType(cqlType = "text") String param1){
        return "";
    }

    @UDA(keySpace = "kingfisher", isReplaceOld = true,version = 2,finalFunc = "sampleMethod", initCond = "{}",stateFunc = "sampleMethod")
    private static void sampleUDA(@CqlType(cqlType = "")String param1,@CqlType(cqlType = "cint") Integer param2,@CqlType(cqlType = "timestamp") Date param3){
    }


}
