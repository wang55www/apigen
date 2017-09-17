package com.alipay.mobile;

import mobileapigen.model.*;
import mobileapigen.popup.actions.NodeTreeTravor;
import mobileapigen.popup.actions.NodeVisitor;
import mobileapigen.popup.actions.VelocityProxy;
import mobileapigen.verification.ClassTypeChecker;
import mobileapigen.verification.FieldDeclarationChecker;
import mobileapigen.verification.MethodDeclarationChecker;
import mobileapigen.verification.Util;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * 生成RPC代码类
 */
public class ApiGen {

    private static List<ITypeBinding> referenceCU = new ArrayList();

    public static void main(String[] args) throws Exception {
        String content = read("/opt/workspace/mfinsnsprod/app/biz/service-gw/src/main/java/com/alipay/mfinsnsprod/biz/service/gw/community/api/question/QuestionGwManager.java"); //java源文件
        //创建解析器
        ASTParser parsert = ASTParser.newParser(AST.JLS4);
        //设定解析器的源代码字符
        parsert.setSource(content.toCharArray());
        //使用解析器进行解析并返回AST上下文结果(CompilationUnit为根节点)
        CompilationUnit result = (CompilationUnit) parsert.createAST(null);
        JavaDefinitionModel javaModel=getJavaModel(result);
        List<JavaDefinitionModel> javaModels=new ArrayList<JavaDefinitionModel>();
        javaModels.add(javaModel);
        mergeInterfaceModel(new ArrayList(javaModels));
        initVelocityTemplate();

        Iterator var15 = javaModels.iterator();
        while(var15.hasNext()) {
            JavaDefinitionModel var14 = (JavaDefinitionModel)var15.next();
//            Util.log("开始生成适配代码，class=" + var14.getPackageName() + "." + var14.getClassName());
            renderJavaFile(var14);
//            this.renderWPFile(var14);
//            var14.setShowHeader(this.addIosHeaderSet);
//            Util.log("IOS的RPC接口适配代码是否添加Header设置参数：" + this.addIosHeaderSet);
//            this.renderCocoahFile(var14);
//            this.renderCocoamFile(var14);
        }

    }

    private static String read(String filename) throws IOException {
        File file = new File(filename);
        byte[] b = new byte[(int) file.length()];
        FileInputStream fis = new FileInputStream(file);
        fis.read(b);
        return new String(b,"GBK");

    }

    private static void test(CompilationUnit result){

        //获取类型
        List types = result.types();
        //取得类型声明
        TypeDeclaration typeDec = (TypeDeclaration) types.get(0);

        //##############获取源代码结构信息#################
        //引用import
        List importList = result.imports();
        //取得包名
        PackageDeclaration packetDec = result.getPackage();
        //取得类名
        String className = typeDec.getName().toString();
        //取得函数(Method)声明列表
        MethodDeclaration methodDec[] = typeDec.getMethods();
        //取得函数(Field)声明列表
        FieldDeclaration fieldDec[] = typeDec.getFields();

        //输出包名
        System.out.println("包:");
        System.out.println(packetDec.getName());
        //输出引用import
        System.out.println("引用import:");
        for (Object obj : importList) {
            ImportDeclaration importDec = (ImportDeclaration) obj;
            System.out.println(importDec.getName());
        }
        //输出类名
        System.out.println("类:");
        System.out.println(className);
        //循环输出函数名称
        System.out.println("========================");
        System.out.println("函数:");
        for (MethodDeclaration method : methodDec) {
         /* System.out.println(method.getName());
          System.out.println("body:");
          System.out.println(method.getBody());
          System.out.println("Javadoc:" + method.getJavadoc());

          System.out.println("Body:" + method.getBody());

          System.out.println("ReturnType:" + method.getReturnType());*/
            System.out.println("=============");
            System.out.println(method);
        }

        //循环输出变量
        System.out.println("变量:");
        for (FieldDeclaration fieldDecEle : fieldDec) {
            //public static
            for (Object modifiObj : fieldDecEle.modifiers()) {
                Modifier modify = (Modifier) modifiObj;
                System.out.print(modify + "-");
            }
            System.out.println(fieldDecEle.getType());
            for (Object obj : fieldDecEle.fragments()) {
                VariableDeclarationFragment frag = (VariableDeclarationFragment) obj;
                System.out.println("[FIELD_NAME:]" + frag.getName());
            }
        }
    }

    private static JavaDefinitionModel getJavaModel(CompilationUnit cu) {
        ModelGen modelGen = new ModelGen();
        cu.accept(new NodeTreeTravor(Arrays.asList(new NodeVisitor[]{modelGen})));
        String binding1 = null;
        if (cu != null && cu.getTypeRoot() != null) {
            binding1 = cu.getTypeRoot().getElementName();
        }

        if (!modelGen.model.checkMethodName()) {
            Util.log(modelGen.model.getClassName() + "中方法重名");
            throw new ApiGenException(modelGen.model.getClassName() + "中方法重名");
        }
        Iterator var7 = referenceCU.iterator();
        while (var7.hasNext()) {
            ITypeBinding binding = (ITypeBinding) var7.next();
            if (!binding.isInterface()) {
                modelGen.model.addImport(binding.getQualifiedName());
            }

            IJavaElement ije;
            for (ije = binding.getJavaElement(); !(ije instanceof ITypeRoot); ije = ije.getParent()) {
                ;
            }
//            enqueue((ITypeRoot)ije);
        }
        referenceCU.clear();
        return modelGen.model;
    }

    private static void mergeInterfaceModel(List<JavaDefinitionModel> models) {
        JavaDefinitionModel infModel = null;
        boolean isFirst = true;
        Iterator it = models.iterator();

        while(true) {
            JavaDefinitionModel model;
            do {
                if(!it.hasNext()) {
                    if(infModel != null) {
                        models.add(infModel);
                    }

                    return;
                }

                model = (JavaDefinitionModel)it.next();
            } while(!model.isInterfaceType());

            if(isFirst) {
                infModel = new JavaDefinitionModel();
                infModel.setClassName(model.getClassName());
                infModel.setPackageName(model.getPackageName());
                isFirst = false;
            }

            Iterator var7 = model.getImports().iterator();

            boolean findit;
            Iterator var10;
            while(var7.hasNext()) {
                ImportInfo methodinfo = (ImportInfo)var7.next();
                findit = false;
                var10 = infModel.getImports().iterator();

                while(var10.hasNext()) {
                    ImportInfo mi = (ImportInfo)var10.next();
                    if(mi.getName().equals(methodinfo.getName())) {
                        findit = true;
                    }
                }

                if(!findit) {
                    infModel.addImport(methodinfo.getName());
                }
            }

            var7 = model.getMethods().iterator();

            while(var7.hasNext()) {
                MethodInfo methodinfo1 = (MethodInfo)var7.next();
                findit = false;
                var10 = infModel.getMethods().iterator();

                MethodInfo mi1;
                while(var10.hasNext()) {
                    mi1 = (MethodInfo)var10.next();
                    if(mi1.getMethodName().equals(methodinfo1.getMethodName())) {
                        findit = true;
                    }
                }

                if(!findit) {
                    mi1 = new MethodInfo(methodinfo1.getMethodNode(), methodinfo1.getOpType(), methodinfo1.isCheckLogin(), methodinfo1.isSignCheck());
                    infModel.addMethodInfo(mi1);
                }
            }

            it.remove();
        }
    }


    private static void initVelocityTemplate() throws Exception {
        String path ="/opt/workspace/apigen/lib/templates_wallet/templates";
        if(path != null && path.trim().length() != 0) {
            VelocityProxy.init(path);
        } else {
            Util.log("Template系统环境变量为空");
            throw new ApiGenException("请先设置系统环境变量:MOBILEAPI_TEMPLATE");
        }
    }

    private static void renderJavaFile(JavaDefinitionModel model) throws Exception, CoreException, UnsupportedEncodingException {
        String androidContent = VelocityProxy.write("", model, "android.vm");
        System.out.println("androidContent:\n"+androidContent);
    }

//
//    private void initSrcFolder(IJavaProject javaProject) throws CoreException {
//        IFolder apiFolder = null;
//        IFolder androidFolder = null;
//        IFolder winphoneFolder = null;
//        IFolder cocoaFolder = null;
//        apiFolder = javaProject.getProject().getFolder("MobileAPI");
//        if(!apiFolder.exists()) {
//            apiFolder.create(true, true, (IProgressMonitor)null);
//        }
//
//        androidFolder = apiFolder.getFolder("android");
//        if(!androidFolder.exists()) {
//            androidFolder.create(true, true, (IProgressMonitor)null);
//        }
//
//        this.androidSrcFolder = androidFolder.getFolder("src");
//        if(!this.androidSrcFolder.exists()) {
//            this.androidSrcFolder.create(true, true, (IProgressMonitor)null);
//        }
//
//        cocoaFolder = apiFolder.getFolder("cocoa");
//        if(!cocoaFolder.exists()) {
//            cocoaFolder.create(true, true, (IProgressMonitor)null);
//        }
//
//        this.cocoaSrcFolder = cocoaFolder.getFolder("src");
//        if(!this.cocoaSrcFolder.exists()) {
//            this.cocoaSrcFolder.create(true, true, (IProgressMonitor)null);
//        }
//
//        winphoneFolder = apiFolder.getFolder("winphone");
//        if(!winphoneFolder.exists()) {
//            winphoneFolder.create(true, true, (IProgressMonitor)null);
//        }
//
//        this.winphoneSrcFolder = winphoneFolder.getFolder("src");
//        if(!this.winphoneSrcFolder.exists()) {
//            this.winphoneSrcFolder.create(true, true, (IProgressMonitor)null);
//        }
//
//    }
}
