package com.maxtropy.javac

import java.io.{BufferedWriter, OutputStreamWriter, StringWriter, Writer}
import java.lang.annotation.Annotation
import java.util
import java.util.Locale
import java.lang.reflect.{Proxy => jProxy}

import com.sun.source.tree.Tree.Kind
import com.sun.source.tree._
import com.sun.source.util._
import com.sun.tools.javac.api.BasicJavacTask
import com.sun.tools.javac.code.Type.ClassType
import com.sun.tools.javac.code.{Flags, Type, TypeTag}
import com.sun.tools.javac.tree.{JCTree, Pretty, TreeMaker}
import com.sun.tools.javac.tree.JCTree._
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag
import com.sun.tools.javac.util.{Log, Names, List => javacList}
import sun.reflect.annotation.AnnotationParser

import scala.collection.JavaConverters._
import scala.collection.{IterableLike, immutable}
import scala.util.Try

class CassandraJavacPlugin extends Plugin {

  override def getName: String = classOf[CassandraJavacPlugin].getSimpleName


  override def init(task: JavacTask, args: String*): Unit = {
    val log = Log.instance(task.asInstanceOf[BasicJavacTask].getContext)

    log.printRawLines(Log.WriterKind.NOTICE, "Hello from " + getName)

    task.addTaskListener(new CassandraTaskListener(task))

  }

  class CassandraTaskListener(val task: JavacTask) extends TaskListener {

    val visitor = new CassandraTreeVisitor(task)

    val udfValidator = new UDFValidator(task)

    override def started(e: TaskEvent): Unit = {
      e.getKind match {
        case TaskEvent.Kind.ANALYZE =>
        case TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND =>
        case _ =>
      }
    }

    override def finished(e: TaskEvent): Unit = {
      e.getKind match {
        case TaskEvent.Kind.PARSE =>
          visitor.log.useSource(e.getCompilationUnit.getSourceFile)
          visitor.scan(e.getCompilationUnit.asInstanceOf[Tree], null)
        case TaskEvent.Kind.ANALYZE =>
          udfValidator.log.useSource(e.getCompilationUnit.getSourceFile)
          udfValidator.scan(e.getCompilationUnit.asInstanceOf[JCTree], null)

        case _ =>
      }
    }
  }


  class UDFValidator(val task: JavacTask) extends TreeScanner[Unit, Tree] {

    def context() = task.asInstanceOf[BasicJavacTask].getContext

    val log = Log.instance(context())

    log.multipleErrors = true

    val symblesTable = Names.instance(context())


    val blacklistedPatterns = List(
      "com.datastax.driver.core.Cluster",
      "com.datastax.driver.core.Metrics",
      "com.datastax.driver.core.NettyOptions",
      "com.datastax.driver.core.Session",
      "com.datastax.driver.core.Statement",
      "com.datastax.driver.core.TimestampGenerator",
      "java.lang.Compiler",
      "java.lang.InheritableThreadLocal",
      "java.lang.Package",
      "java.lang.Process",
      "java.lang.ProcessBuilder",
      "java.lang.ProcessEnvironment",
      "java.lang.ProcessImpl",
      "java.lang.Runnable",
      "java.lang.Runtime",
      "java.lang.Shutdown",
      "java.lang.Thread",
      "java.lang.ThreadGroup",
      "java.lang.ThreadLocal",
      "java.lang.instrument.",
      "java.lang.invoke.",
      "java.lang.management.",
      "java.lang.ref.",
      "java.lang.reflect.",
      "java.util.ServiceLoader",
      "java.util.Timer",
      "java.util.concurrent.",
      "java.util.function.",
      "java.util.jar.",
      "java.util.logging.",
      "java.util.prefs.",
      "java.util.spi.",
      "java.util.stream.",
      "java.util.zip.",
    ).map(str => symblesTable.fromString(str))


    override def visitMethod(node: MethodTree, p: Tree): Unit = {
      if (CassandraJavacPlugin.getAnnotation(classOf[UDF], node).isDefined) {
        super.visitMethod(node, p)
      }
    }


    override def visitMemberSelect(node: MemberSelectTree, p: Tree): Unit = {
      node match {
        case jcfield: JCFieldAccess =>
          jcfield.`type` match {
            case ct: ClassType =>
              //              blacklistedPatterns.foldLeft(false)( (found,blackName) =>  !found && ct.typarams_field.head.tsym.getQualifiedName.startsWith(blackName))
              blacklistedPatterns.foreach(name => {
                val sym = if (ct.isParameterized) {
                  ct.typarams_field.head.tsym
                } else {
                  ct.tsym
                }
                if (sym.getQualifiedName.startsWith(name)) {
                  log.error(DiagnosticFlag.RESOLVE_ERROR, node.asInstanceOf[JCTree].pos(), "proc.messager", s"accessing ${ct.typarams_field.head.tsym.getQualifiedName} is forbidden in UDF")
                }

              })

            case _ =>
          }
        case _ =>
      }
      super.visitMemberSelect(node, p)
    }

    override def visitMemberReference(node: MemberReferenceTree, p: Tree): Unit = {
      super.visitMemberReference(node, p)
    }
  }


  class CassandraTreeVisitor(val task: JavacTask) extends TreePathScanner[Unit, Tree] {

    import CassandraJavacPlugin._

    def context() = task.asInstanceOf[BasicJavacTask].getContext

    val trees = Trees.instance(task)

    val types = task.getTypes
    val sourcePositions = trees.getSourcePositions
    val log = Log.instance(context())

    val symblesTable = Names.instance(context())


    val factory = TreeMaker.instance(context())

    @inline
    def info(msg: String) = log.printRawLines(Log.WriterKind.NOTICE, msg)


    override def visitMethod(node: MethodTree, p: Tree): Unit = {
      val maker = factory.at(node.asInstanceOf[JCTree].pos())

      def getUDFName(a: Annotation, backup: String): String = {
        a match {
          case udf: UDF =>
            s"${udf.keySpace()}.${
              if (udf.name() != null && udf.name().nonEmpty) {
                udf.name()
              } else {
                backup
              }
            }_v${udf.version()}"
          case uda: UDA =>
            s"${uda.keySpace()}.${
              if (uda.name() != null && uda.name().nonEmpty) {
                uda.name()
              } else {
                backup
              }
            }_v${uda.version()}"
          case _ =>
            backup
        }
      }

      getAnyAnnotationOf(List(classOf[UDF], classOf[UDA]), node)
        .flatMap {
          case a: Annotation =>
            val flags = node.asInstanceOf[JCMethodDecl].getModifiers.flags
            if ((flags & Flags.PRIVATE) == 0 || (flags & Flags.STATIC) == 0) {
              Left(Some(CompileException(s" UDF/UDA should be private & static ", node)))
            } else {
              (getAnnotation(classOf[CqlType], node).map(cqlTypeAnnotationInstance => cqlTypeAnnotationInstance.cqlType()) match {
                case Some(returnType) =>
                  if(returnType != null && returnType.nonEmpty)
                    Right((a, returnType))
                  else
                    Left(Some(CompileException(s"CqlType for ${node.getName} should not be null or empty",node)))
                case None if a.isInstanceOf[UDA] =>
                  if(!node.getReturnType.asInstanceOf[JCPrimitiveTypeTree].typetag.equals(TypeTag.VOID)){
                    Left(Some(CompileException("UDA must have void return type",node)))
                  }else{
                    Right((a, ""))
                  }
                case None =>
                  Left(Some(CompileException(s"no return type specified for UDF function ${node.getName.toString}", node)))
              }).flatMap {
                case (a, returnType) =>
                  node.getParameters.asScala.foldLeftEither[StringBuilder, Option[CompileException]](new StringBuilder) { (prev, vTree) =>
                    val sbuilder = prev.value
                    if (sbuilder.nonEmpty) {
                      sbuilder.append(",")
                    }
                    (vTree.getKind match {
                      case Tree.Kind.VARIABLE =>
                        getAnnotation(classOf[CqlType], vTree).map(_.cqlType())
                      case _ =>
                        None
                    }) match {
                      case Some(paramType) =>
                        if(paramType != null && paramType.nonEmpty){
                          sbuilder
                            .append(vTree.getName.toString)
                            .append(" ")
                            .append(paramType)
                          prev
                        }else{
                          Left(Some(CompileException(s"CqlType for ${vTree.getName} must not be null or empty",vTree)))
                        }
                      case None =>
                        Left(Some(CompileException(s"must specify CqlType for parameter ${vTree.getName}", vTree)))
                    }
                  }.flatMap(paramListBuilder => Right((a, paramListBuilder.result(), returnType)))
              }
            }
          case _ =>
            Left(None)
        }
        .flatMap {
          case (udf: UDF, parameterList, returnType) =>
            val funcName = getUDFName(udf, node.getName.toString)
            val strWriter = new StringWriter(512)

            val pretty = new Pretty(strWriter,true)
            node.getBody.getStatements.forEach( st => pretty.printStat(st.asInstanceOf[JCTree]))
            strWriter.flush()

            Right((funcName, returnType,
              factory.at(-1).Literal(
                s"CREATE ${
                  if (udf.isReplaceOld) {
                    "OR REPLACE"
                  } else {
                    "IF NOT EXISTS"
                  }
                } FUNCTION $funcName" +
                  s"( $parameterList ) RETURNS NULL ON NULL INPUT RETURNS $returnType LANGUAGE java AS $$$$ ${strWriter.toString} $$$$"
              )
            ))
          case (uda: UDA, parameterList, returnType) =>
            val udaName = getUDFName(uda, node.getName.toString)

            val statement: JCExpression = maker.Binary(JCTree.Tag.PLUS,
              maker.Literal(s"CREATE ${
                if (uda.isReplaceOld) {
                  "OR REPLACE"
                } else {
                  "IF NOT EXISTS"
                }
              } AGGREGATE $udaName " +
                s"( $parameterList ) SFUNC "),
              maker.Binary(Tag.PLUS,
                maker.Apply(javacList.nil[JCExpression](), maker.Ident(symblesTable.fromString(uda.stateFunc())), javacList.nil[JCExpression]()),
                maker.Binary(Tag.PLUS,
                  maker.Literal(" STYPE "),
                  maker.Binary(Tag.PLUS,
                    maker.Apply(javacList.nil[JCExpression](), maker.Ident(symblesTable.fromString(uda.stateFunc() + "_stype")), javacList.nil[JCExpression]()),
                    maker.Binary(Tag.PLUS,
                      if (uda.finalFunc().nonEmpty ) {
                        maker.Binary(JCTree.Tag.PLUS,
                          maker.Literal(" FINALFUNC "),
                          maker.Apply(javacList.nil[JCTree.JCExpression](),maker.Ident(symblesTable.fromString(uda.finalFunc())),javacList.nil[JCTree.JCExpression]())
                        )
                      } else {
                        maker.Literal(" ").asInstanceOf[JCExpression] //just make IDE stop reporting type mismatch
                      },
                      maker.Literal(s" INITCOND ${uda.initCond()}")
                    )
                  )
                ),
              )
            )

            Right((udaName, returnType, statement))
          case _ =>
            Left(None)
        }
        .flatMap(pair => {
          val (name, returnType, statement) = pair

          val maker = factory.at(-1)

          val modifiers = maker.Modifiers(Flags.PUBLIC | Flags.STATIC)

          val resType = maker.Select(maker.Select(maker.Ident(symblesTable.fromString("java")), symblesTable.fromString("lang")), symblesTable.fromString("String"))
          //          val resType = maker.Ident(symblesTable.fromString("String"))

          val body = maker.Block(0L,
            javacList.of[JCStatement](
              maker.Return(statement)
            )
          )
          val methods = Seq(
            factory.at(node.asInstanceOf[JCTree].pos())
              .MethodDef(
                modifiers, //modifiers
                symblesTable.fromString(s"${node.getName}_cqlStr"), // name
                resType,
                javacList.nil[JCTypeParameter](),
                javacList.nil[JCVariableDecl](),
                javacList.nil[JCExpression](),
                body,
                null
              ),
            factory.at(node.asInstanceOf[JCTree].pos())
              .MethodDef(
                modifiers,
                node.asInstanceOf[JCMethodDecl].getName,
                resType,
                javacList.nil[JCTypeParameter](),
                javacList.nil[JCVariableDecl](),
                javacList.nil[JCExpression](),
                maker.Block(0L,
                  javacList.of[JCStatement](
                    maker.Return(
                      maker.Literal(name)
                    )
                  )
                ),
                null
              ),
          ) ++ (if (returnType.nonEmpty) {
            Seq(factory.at(node.asInstanceOf[JCTree].pos())
              .MethodDef(
                modifiers,
                symblesTable.fromString(s"${node.getName}_stype"),
                resType,
                javacList.nil[JCTypeParameter](),
                javacList.nil[JCVariableDecl](),
                javacList.nil[JCExpression](),
                maker.Block(0L,
                  javacList.of[JCStatement](
                    maker.Return(
                      maker.Literal(returnType)
                    )
                  )
                ),
                null
              )
            )
          } else {
            Nil
          })
          Right(methods)
        })
        .flatMap(methodDecls => {
          p match {
            case parent: JCClassDecl =>
              methodDecls.foreach(method => {
                parent.defs = parent.defs.append(method)
              })
              Right[Option[CompileException], Boolean](true)
            case _ =>
              Left[Option[CompileException], Boolean](Some(CompileException(s"UDF [${node.getName.toString}] should defined in a class", node)))
          }
        }) match {
        case Left(Some(error: CompileException)) =>
          log.error(error.pos.asInstanceOf[JCTree].pos(), "proc.messager", error.msg)
        case _ =>
      }
    }


    override def visitClass(node: ClassTree, p: Tree): Unit = {
      //      info(s"visiting [${node.getSimpleName}]")
      node.getMembers.asScala.foreach {
        case methodTree: MethodTree =>
          //          info(s"scaning method:${methodTree.getName}")
          this.visitMethod(methodTree, node)
        case _ =>
      }
    }
  }

}


object CassandraJavacPlugin {

  case class CompileException(msg: String, pos: Tree) extends Exception(msg)

  implicit class RighTraversableOnce[T](t: Iterable[T]) {

    def foldLeftEither[B, E](identity: B)(fold: (Right[E, B], T) => Either[E, B]): Either[E, B] = {
      val iter = t.iterator
      var result: Right[E, B] = Right(identity)
      while (iter.hasNext) {
        fold(result, iter.next()) match {
          case left: Left[E, B] =>
            return left
          case right: Right[E, B] =>
            result = right
        }
      }
      result
    }
  }


  def getAnnotation[T <: Annotation](clazz: Class[T], tree: Tree): Option[T] = {
    (tree match {
      case variableDecl: JCVariableDecl =>
        Some(variableDecl.getModifiers)
      case methodDecl: JCMethodDecl =>
        Some(methodDecl.getModifiers)
      case _ =>
        None
    }).flatMap(
      _.annotations.asScala.find(a => clazz.getCanonicalName.equals(a.annotationType.toString) || clazz.getSimpleName.equals(a.annotationType.toString))
    )
      .map(jcAnnotation => {
        jProxy.newProxyInstance(clazz.getClassLoader, scala.Array[Class[_]](clazz), new AnnotationInvocationHandler[T](clazz,
          jcAnnotation.getArguments.asScala.foldLeft(immutable.HashMap.empty[String, AnyRef]) {
            case (map, expr: JCAssign) =>
              try {
                val name = expr.lhs.asInstanceOf[JCIdent].getName.toString
                val value = expr.rhs.asInstanceOf[JCLiteral].getValue
                map + ((name, value))
              } catch {
                case e: Throwable =>
                  map
              }
            case (map, _) =>
              map
          }
        )).asInstanceOf[T]
      })
  }


  def getAnyAnnotationOf(classes: List[Class[_]], tree: Tree): Either[Option[CompileException], Annotation] = {
    (tree match {
      case variableDecl: JCVariableDecl =>
        Right(variableDecl.getModifiers.annotations.asScala.toSeq)
      case methodDecl: JCMethodDecl =>
        Right(methodDecl.getModifiers.annotations.asScala.toSeq)
      case _ =>
        Left(None)
    }).flatMap {
      case annotations: Seq[JCAnnotation] =>
        val someAnnotations = annotations
          .flatMap(jca =>
            classes.find(clazz =>
              clazz.getCanonicalName.equals(jca.annotationType.toString) || clazz.getSimpleName.equals(jca.annotationType.toString)
            ).map((jca, _))
          )
          .map {
            case (jcAnnotation, clazz: Class[Annotation]) =>
              jProxy.newProxyInstance(clazz.getClassLoader, scala.Array[Class[_]](clazz), new AnnotationInvocationHandler(clazz,
                jcAnnotation.getArguments.asScala.foldLeft(immutable.HashMap.empty[String, AnyRef]) {
                  case (map, expr: JCAssign) =>
                    try {
                      val name = expr.lhs.asInstanceOf[JCIdent].getName.toString
                      val value = expr.rhs.asInstanceOf[JCLiteral].getValue
                      map + ((name, value))
                    } catch {
                      case e: Throwable =>
                        map
                    }
                  case (map, _) =>
                    map
                }
              )).asInstanceOf[Annotation]
            case _ =>
              null
          }

        if (someAnnotations.isEmpty) {
          Left(None)
        } else if (someAnnotations.tail.nonEmpty) {
          Left(Some(CompileException("can not have both UDF and UDA annotation", tree)))
        } else {
          Right(someAnnotations.head)
        }
      case x@_ =>
        Left(Some(CompileException(s"unable to handle $x", tree)))
    }
  }


}



