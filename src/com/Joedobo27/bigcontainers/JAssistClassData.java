package com.Joedobo27.bigcontainers;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;

@SuppressWarnings("unused")
class JAssistClassData{

    private CtClass ctClass;
    private ClassFile classFile;
    private ConstPool constPool;

    JAssistClassData(String classPath, ClassPool classPool) throws NotFoundException {
        ctClass = classPool.get(classPath);
        classFile = ctClass.getClassFile();
        constPool = classFile.getConstPool();
    }

    CtClass getCtClass() {
        return ctClass;
    }

    ClassFile getClassFile() {
        return classFile;
    }

    ConstPool getConstPool() {
        return constPool;
    }
}
