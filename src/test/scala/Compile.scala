import java.io.{File, StringWriter}
import java.nio.charset.Charset

import javax.tools._
import java.util
import java.util.Locale

import scala.collection.JavaConverters._
import scala.util.Try

object Compile {


  class ConsoleDiagnosticCollector extends DiagnosticListener[JavaFileObject] {
    override def report(diagnostic: Diagnostic[_ <: JavaFileObject]): Unit = {
      printf("%s , line %d in %s",diagnostic.getMessage(Locale.getDefault()),diagnostic.getLineNumber,Try{diagnostic.getSource.getName})
    }
  }


  def main(args:Array[String]): Unit ={

    val compiler = ToolProvider.getSystemJavaCompiler

    val output = new StringWriter(100)


    val diagnostic = new ConsoleDiagnosticCollector


    val fileManager = compiler.getStandardFileManager(diagnostic,Locale.getDefault(),Charset.defaultCharset())

    val source = fileManager.getJavaFileObjects(new File("src/test/java/Sample.java"))


    val hasErrors = !compiler.getTask(
      output,
      fileManager,
      diagnostic,
      List("-Xplugin:CassandraJavacPlugin").asJava,
      Nil.asJava,
      source
    ).call()

    println(s"Compile Finished , hasErrors:${hasErrors}")


    println(s"output ${output.toString}")

  }
}
