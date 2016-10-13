package com.Joedobo27.bigcontainers;

import com.Joedobo27.common.Common;
import com.wurmonline.server.items.*;
import javassist.*;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.Joedobo27.bigcontainers.BytecodeTools.addConstantPoolReference;
import static com.Joedobo27.bigcontainers.BytecodeTools.findConstantPoolReference;
import static com.Joedobo27.bigcontainers.BytecodeTools.findReplaceCodeIterator;


public class BigContainersBAEAMod implements WurmServerMod, Initable, ServerStartedListener, Configurable {

    private static boolean makeContainersBig = false;
    private static boolean unlimitedSpace = false;
    private static boolean removeOnePerTileLimits = false;
    private static boolean resizePelt = false;
    private static boolean makeItemsBulk = false;
    private static boolean makeItemsCombine = false;
    private static boolean removeInsideOutsideLimits = false;
    private static int[] bigLiquidHolders;
    private static int[] makeBulkItems;
    private static int[] makeCombineItems;
    private static JAssistClassData creationEntry;
    private static JAssistClassData itemClass;
    private static JAssistClassData cargoTransportationMethods;

    private static ClassPool classPool;

    private static Logger logger = Logger.getLogger(BigContainersBAEAMod.class.getName());

    @Override
    public void configure(Properties properties) {
        makeContainersBig = Boolean.parseBoolean(properties.getProperty("makeContainersBig", Boolean.toString(makeContainersBig)));
        unlimitedSpace = Boolean.parseBoolean(properties.getProperty("unlimitedSpace", Boolean.toString(unlimitedSpace)));
        removeOnePerTileLimits = Boolean.parseBoolean(properties.getProperty("removeOnePerTileLimits", Boolean.toString(removeOnePerTileLimits)));
        resizePelt = Boolean.parseBoolean(properties.getProperty("resizePelt", Boolean.toString(resizePelt)));
        makeItemsBulk = Boolean.parseBoolean(properties.getProperty("makeItemsBulk", Boolean.toString(makeItemsBulk)));
        makeItemsCombine = Boolean.parseBoolean(properties.getProperty("makeItemsCombine", Boolean.toString(makeItemsCombine)));
        removeInsideOutsideLimits = Boolean.parseBoolean(properties.getProperty("removeInsideOutsideLimits", Boolean.toString(removeInsideOutsideLimits)));

        bigLiquidHolders = Arrays.stream(properties.getProperty("bigLiquidHolders", Arrays.toString(bigLiquidHolders)).replaceAll("\\s", "").split(",")).mapToInt(Integer::parseInt).toArray();
        makeBulkItems = Arrays.stream(properties.getProperty("makeBulkItems", Arrays.toString(makeBulkItems)).replaceAll("\\s", "").split(",")).mapToInt(Integer::parseInt).toArray();
        makeCombineItems = Arrays.stream(properties.getProperty("makeCombineItems", Arrays.toString(makeCombineItems)).replaceAll("\\s", "").split(",")).mapToInt(Integer::parseInt).toArray();
    }

    @Override
    public void init()   {
        try {
            classPool = HookManager.getInstance().getClassPool();
            creationEntry = new JAssistClassData("com.wurmonline.server.items.CreationEntry", classPool);
            itemClass = new JAssistClassData("com.wurmonline.server.items.Item", classPool);
            cargoTransportationMethods = new JAssistClassData("com.wurmonline.server.behaviours.CargoTransportationMethods", classPool);

            makeItemsCombineBytecode();
            unlimitedSpaceBytecode();
        } catch (NotFoundException | FileNotFoundException |CannotCompileException | BadBytecode e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @Override
    public void onServerStarted() {
        /*
        int[] bigLiquidHolders = {ItemList.barrelHuge, ItemList.barrelLarge, ItemList.stoneFountain,
                ItemList.stoneFountain2};
        int[] makeBulk = {ItemList.logHuge, ItemList.pelt, ItemList.saddle, ItemList.stoneKeystone,
                ItemList.marbleKeystone, ItemList.fishingHookWood, ItemList.fishingHookIron, ItemList.fishingHookWoodAndString,
                ItemList.fishingHookIronAndString}; // in addition by default all items of type fish or gem.
        int[] makeCombine = {ItemList.cochineal, ItemList.woad, ItemList.acorn};
        */
        try {
            int makeContainersBigCount = makeContainersBigReflection(bigLiquidHolders);
            int removeOnePerTileLimitsCount = removeOnePerTileLimitsReflection();
            resizePeltReflection();
            int makeItemsBulkCount = makeItemsBulkReflection(makeBulkItems);
            int makeItemsCombineCount = makeItemsCombineReflection(makeCombineItems);
            int removeInsideOutsideLimitsCount = removeInsideOutsideLimitsReflection();

            logger.log(Level.INFO, "Number of Containers maxed to 1,728,000 L is " + Integer.toString(makeContainersBigCount));
            logger.log(Level.INFO, "Number of one per tile restrictions removed is " + Integer.toString(removeOnePerTileLimitsCount));
            logger.log(Level.INFO, "Number of items marked as bulk is " + Integer.toString(makeItemsBulkCount));
            logger.log(Level.INFO, "Number of items marked as combine is " + Integer.toString(makeItemsCombineCount));
            logger.log(Level.INFO, "Number of inside or outside only flags removed is " + Integer.toString(removeInsideOutsideLimitsCount));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    private static void makeItemsCombineBytecode() throws CannotCompileException, FileNotFoundException, NotFoundException, BadBytecode {
        if (!makeItemsCombine)
            return;

        // In CreationEntry.checkSaneAmounts()
        // Byte code change: this.objectCreated != 73 to this.getObjectCreated() == 73
        // Do this because it's not possible to instrument on a field and have the replace function use a returned value from a hook method.
        JAssistMethodData checkSaneAmounts = new JAssistMethodData(creationEntry,
                "(Lcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/ItemTemplate;Lcom/wurmonline/server/creatures/Creature;Z)V",
                "checkSaneAmounts");

        int[] makeItemsCombineSuccesses = new int[2];
        Arrays.fill(makeItemsCombineSuccesses, 0);

        boolean isModifiedCheckSaneAmounts = true;
        byte[] findPoolResult;
        try {
            findConstantPoolReference(creationEntry.getConstPool(),
                    "// Method com/Joedobo27/common/Common.checkSaneAmountsExceptionsHook:(III)I");
        } catch (UnsupportedOperationException e){
            isModifiedCheckSaneAmounts = false;
        }

        if (isModifiedCheckSaneAmounts)
            Arrays.fill(makeItemsCombineSuccesses, 1);
        if (!isModifiedCheckSaneAmounts) {
            Bytecode find = new Bytecode(creationEntry.getConstPool());
            find.addOpcode(Opcode.ALOAD_0);
            find.addOpcode(Opcode.GETFIELD);
            findPoolResult = findConstantPoolReference(creationEntry.getConstPool(), "// Field objectCreated:I");
            find.add(findPoolResult[0], findPoolResult[1]);
            find.addOpcode(Opcode.BIPUSH);
            find.add(73);

            Bytecode replace = new Bytecode(creationEntry.getConstPool());
            replace.addOpcode(Opcode.ALOAD_0);
            replace.addOpcode(Opcode.INVOKEVIRTUAL);
            findPoolResult = addConstantPoolReference(creationEntry.getConstPool(), "// Method com/wurmonline/server/items/CreationEntry.getObjectCreated:()I");
            replace.add(findPoolResult[0], findPoolResult[1]);
            replace.addOpcode(Opcode.BIPUSH);
            replace.add(73);

            boolean replaceResult = findReplaceCodeIterator(checkSaneAmounts.getCodeIterator(), find, replace);
            makeItemsCombineSuccesses[0] = replaceResult ? 1 : 0;
            logger.log(Level.FINE, "checkSaneAmounts find and replace: " + Boolean.toString(replaceResult));

            checkSaneAmounts.getCtMethod().instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals("getObjectCreated", methodCall.getMethodName())) {
                        methodCall.replace("$_ = com.Joedobo27.common.Common.checkSaneAmountsExceptionsHook( $0.getObjectCreated(), sourceMax, targetMax);");
                        logger.log(Level.FINE, "CreationEntry.class, checkSaneAmounts(), installed hook at line: " + methodCall.getLineNumber());
                        makeItemsCombineSuccesses[1] = 1;
                    }
                }
            });
        }
        evaluateChangesArray(makeItemsCombineSuccesses, "makeItemsCombine");
    }

    private static void unlimitedSpaceBytecode() throws NotFoundException, CannotCompileException{
        if (!unlimitedSpace)
            return;

        int[] successes = new int[14];
        Arrays.fill(successes, 0);

        // Alter Item.hasSpaceFor() so it always returns true.
        itemClass.getCtClass().getMethod("hasSpaceFor", "(I)Z").setBody("return true;");

        // Alter CargoTransportationMethods.targetCanNotBeInsertedCheck() so the volume and x,y,z measurements return max int.
        JAssistMethodData targetCanNotBeInsertedCheck = new JAssistMethodData(cargoTransportationMethods,
                "(Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/behaviours/Vehicle;Lcom/wurmonline/server/creatures/Creature;)Z",
                "targetCanNotBeInsertedCheck");
        String methodName = targetCanNotBeInsertedCheck.getCtMethod().getName();

        targetCanNotBeInsertedCheck.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getContainerSizeX", methodCall.getMethodName())){
                    logger.log(Level.FINE, methodName + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[0] = 1;
                }
                else if (Objects.equals("getContainerSizeY", methodCall.getMethodName())){
                    logger.log(Level.FINE, methodName + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[1] = 1;
                }
                else if (Objects.equals("getContainerSizeZ", methodCall.getMethodName())){
                    logger.log(Level.FINE, methodName + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[2] = 1;
                }
            }
        });

        // Alter Item.insertItem() so the volume and x,y,z size fetching methods all return max int.
        CtMethod ctmInsertItem = itemClass.getCtClass().getMethod("insertItem", "(Lcom/wurmonline/server/items/Item;Z)Z");
        ctmInsertItem.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getContainerSizeX", methodCall.getMethodName())){
                    logger.log(Level.FINE, targetCanNotBeInsertedCheck.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[3] = 1;
                }
                else if (Objects.equals("getContainerSizeY", methodCall.getMethodName())){
                    logger.log(Level.FINE, targetCanNotBeInsertedCheck.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[4] = 1;
                }
                else if (Objects.equals("getContainerSizeZ", methodCall.getMethodName())){
                    logger.log(Level.FINE, targetCanNotBeInsertedCheck.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[5] = 1;
                }
            }
        });

        // About 2780 in bytecode
        // Alter Item.moveToItem() so all the x,y,z size fetching methods all return max int.
        // note that changing the return value will alter indexOfBytecode() results for things afterwards. It may not match javap.exe.

        JAssistMethodData moveToItem = new JAssistMethodData(itemClass,
                "(Lcom/wurmonline/server/creatures/Creature;JZ)Z", "moveToItem");
        moveToItem.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getSizeX", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 2780){
                    logger.log(Level.FINE, moveToItem.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[6] = 1;
                }
                else if (Objects.equals("getSizeY", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 2801){
                    logger.log(Level.FINE, moveToItem.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[7] = 1;
                }
                else if (Objects.equals("getSizeZ", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 2822) {
                    logger.log(Level.FINE, moveToItem.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[8] = 1;
                }
            }
        });

        //Alter Item.testInsertHollowItem() so volume and measurements return max int.
        JAssistMethodData testInsertHollowItem = new JAssistMethodData(itemClass,
                "(Lcom/wurmonline/server/items/Item;Z)Z", "testInsertHollowItem");
        testInsertHollowItem.getCtMethod().instrument(new ExprEditor() {
           @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getContainerSizeX", methodCall.getMethodName())){
                    logger.log(Level.FINE, testInsertHollowItem.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[9] = 1;
                }
                else if (Objects.equals("getContainerSizeY", methodCall.getMethodName())){
                    logger.log(Level.FINE, testInsertHollowItem.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[10] = 1;
                }
                else if (Objects.equals("getContainerSizeZ", methodCall.getMethodName())){
                    logger.log(Level.FINE, testInsertHollowItem.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    successes[11] = 1;
                }
            }
        });

        JAssistClassData MethodsItems = new JAssistClassData("com.wurmonline.server.behaviours.MethodsItems", classPool);
        JAssistMethodData fillContainer1 = new JAssistMethodData(MethodsItems,
                "(Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/creatures/Creature;)V",
                "fillContainer");
        JAssistMethodData fillContainer2 = new JAssistMethodData(MethodsItems,
                "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/items/Item;Lcom/wurmonline/server/creatures/Creature;)V",
                "fillContainer");
        // When getFreeVolume is altered to always return max.integer it messed up filling containers with liquid.
        // In fillContainer() redirect the getFreeVolume call to a hook in mod that returns the same as WU default.
        fillContainer1.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getFreeVolume", methodCall.getMethodName())) {
                    logger.log(Level.FINE, fillContainer1.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = com.Joedobo27.bigcontainers.BigContainersBAEAMod.getFreeVolumeHook(source);");
                    successes[12] = 1;
                }
            }
        });
        fillContainer2.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getFreeVolume", methodCall.getMethodName())) {
                    logger.log(Level.FINE, fillContainer2.getCtMethod().getName() + " method,  edit call call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = com.Joedobo27.bigcontainers.BigContainersBAEAMod.getFreeVolumeHook(source);");
                    successes[13] = 1;
                }
            }
        });

        JAssistMethodData getFreeVolume = new JAssistMethodData(itemClass,
                "()I", "getFreeVolume");
        getFreeVolume.getCtMethod().setBody("return Integer.MAX_VALUE;");

        evaluateChangesArray(successes, "unlimitedSpace");
    }

    private static int makeContainersBigReflection(int[] makeBig) throws NoSuchFieldException, IllegalAccessException {
        int makeContainersBigCount = 0;
        if (!makeContainersBig)
            return makeContainersBigCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldUsesSpecifiedContainerSizes = ReflectionUtil.getField(ItemTemplate.class, "usesSpecifiedContainerSizes");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            if (template.isHollow() && (!template.isContainerLiquid() || Arrays.stream(makeBig).filter(value -> Objects.equals(value, itemId)).count() > 0)) {
                if (!template.usesSpecifiedContainerSizes())
                    ReflectionUtil.setPrivateField(template, fieldUsesSpecifiedContainerSizes, Boolean.TRUE);
                template.setContainerSize(1200, 1200, 1200);
                makeContainersBigCount++;
            }
        }
        return makeContainersBigCount;
    }

    private static int removeOnePerTileLimitsReflection() throws NoSuchFieldException, IllegalAccessException {
        int removeOnePerTileLimitsCount = 0;
        if (!removeOnePerTileLimits)
            return removeOnePerTileLimitsCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldOnePerTile = ReflectionUtil.getField(ItemTemplate.class, "onePerTile");
        for (ItemTemplate template : fieldTemplates.values()) {
            Boolean onePerTile;
            onePerTile = ReflectionUtil.getPrivateField(template, fieldOnePerTile);
            if (onePerTile) {
                ReflectionUtil.setPrivateField(template, fieldOnePerTile, Boolean.FALSE);
                removeOnePerTileLimitsCount++;
            }
        }
        return removeOnePerTileLimitsCount;
    }

    private static void resizePeltReflection() throws NoSuchFieldException, IllegalAccessException {
        if (!resizePelt)
            return;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldCentimetersX = ReflectionUtil.getField(ItemTemplate.class, "centimetersX");
        Field fieldCentimetersY = ReflectionUtil.getField(ItemTemplate.class, "centimetersY");
        Field fieldCentimetersZ = ReflectionUtil.getField(ItemTemplate.class, "centimetersZ");
        Field fieldWeight = ReflectionUtil.getField(ItemTemplate.class, "weight");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            if (itemId == ItemList.pelt) {
                try {
                    ReflectionUtil.setPrivateField(template, fieldCentimetersX, 10);
                    ReflectionUtil.setPrivateField(template, fieldCentimetersY, 10);
                    ReflectionUtil.setPrivateField(template, fieldCentimetersZ, 10);
                    ReflectionUtil.setPrivateField(template, fieldWeight, 100);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                logger.log(Level.INFO, "Pelt resized.");
            }
        }
    }

    private static int makeItemsBulkReflection(int[] makeBulk) throws NoSuchFieldException, IllegalAccessException {
        int makeItemsBulkCount = 0;
        if (!makeItemsBulk)
            return makeItemsBulkCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldGem = ReflectionUtil.getField(ItemTemplate.class, "gem");
        Field fieldBulk = ReflectionUtil.getField(ItemTemplate.class, "bulk");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            Boolean isGem = ReflectionUtil.getPrivateField(template, fieldGem);
            if (template.isFish() || isGem || Arrays.stream(makeBulk).filter(value -> Objects.equals(value, itemId)).count() > 0) {
                ReflectionUtil.setPrivateField(template, fieldBulk, Boolean.TRUE);
                makeItemsBulkCount++;
            }
        }
        return makeItemsBulkCount;
    }

    private static int makeItemsCombineReflection(int[] makeCombine) throws NoSuchFieldException, IllegalAccessException {
        int makeItemsCombineCount = 0;
        if (!makeItemsCombine)
            return makeItemsCombineCount;
        int[] exceptions = {ItemList.woad, ItemList.dyeBlue, ItemList.acorn, ItemList.tannin,
                ItemList.cochineal, ItemList.dyeRed, ItemList.dye, ItemList.lye};
        Common.addExceptions(exceptions);

        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldCombine = ReflectionUtil.getField(ItemTemplate.class, "combine");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            if (Arrays.stream(makeCombine).filter(value -> Objects.equals(value, itemId)).count() > 0) {
                ReflectionUtil.setPrivateField(template, fieldCombine, Boolean.TRUE);
                makeItemsCombineCount++;
            }
        }
        return makeItemsCombineCount;
    }

    private static int removeInsideOutsideLimitsReflection() throws NoSuchFieldException, IllegalAccessException {
        int removeInsideOutsideLimitsCount = 0;
        if (!removeInsideOutsideLimits)
            return removeInsideOutsideLimitsCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldOutsideonly = ReflectionUtil.getField(ItemTemplate.class, "outsideonly");
        Field fieldInsideOnly = ReflectionUtil.getField(ItemTemplate.class, "insideOnly");
        for (ItemTemplate template : fieldTemplates.values()) {
            Boolean insideOnly = ReflectionUtil.getPrivateField(template, fieldInsideOnly);
            if (template.isOutsideOnly() || insideOnly) {
                try {
                    ReflectionUtil.setPrivateField(template, fieldOutsideonly, Boolean.FALSE);
                    ReflectionUtil.setPrivateField(template, fieldInsideOnly, Boolean.FALSE);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                removeInsideOutsideLimitsCount++;
            }
        }
        return removeInsideOutsideLimitsCount;
    }

    @SuppressWarnings("unused")
    public static int getFreeVolumeHook(Item item) {
        return item.getContainerVolume() - getUsedVolume(item);
    }

    private static int getUsedVolume(Item item) {
        try {
            Method getUsedVolume = Class.forName("com.wurmonline.server.items.Item").getDeclaredMethod("getUsedVolume");
            return (int) getUsedVolume.invoke(item);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return -1;
    }

    private static void evaluateChangesArray(int[] ints, String option) {
        boolean changesSuccessful = !Arrays.stream(ints).anyMatch(value -> value == 0);
        if (changesSuccessful) {
            logger.log(Level.INFO, option + " option changes SUCCESSFUL");
        } else {
            logger.log(Level.INFO, option + " option changes FAILURE");
            logger.log(Level.FINE, Arrays.toString(ints));
        }
    }
}