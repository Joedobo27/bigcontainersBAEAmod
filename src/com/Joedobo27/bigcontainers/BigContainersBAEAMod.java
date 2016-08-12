package com.Joedobo27.bigcontainers;

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

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.Joedobo27.bigcontainers.BytecodeTools.addConstantPoolReference;
import static com.Joedobo27.bigcontainers.BytecodeTools.findConstantPoolReference;
import static com.Joedobo27.bigcontainers.BytecodeTools.findReplaceCodeIterator;


@SuppressWarnings({"UnusedAssignment", "unused"})
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
    private static JAssistClassData terraforming;

    private static ClassPool pool;

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
    public void init() {
        try {
            pool = HookManager.getInstance().getClassPool();
            creationEntry = new JAssistClassData("com.wurmonline.server.items.CreationEntry", pool);
            itemClass = new JAssistClassData("com.wurmonline.server.items.Item", pool);
            cargoTransportationMethods = new JAssistClassData("com.wurmonline.server.behaviours.CargoTransportationMethods", pool);
            terraforming = new JAssistClassData("com.wurmonline.server.behaviours.Terraforming", pool);

            makeItemsCombineBytecode();
            unlimitedSpaceBytecode();
        } catch (NotFoundException | CannotCompileException | BadBytecode e) {
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

    private static void makeItemsCombineBytecode() throws CannotCompileException, NotFoundException, BadBytecode {
        if (!makeItemsCombine)
            return;

        // In CreationEntry.checkSaneAmounts()
        // Byte code change: this.objectCreated != 73 to this.getObjectCreated() == 73
        // Do this because it's not possible to instrument on a field and have the replace function use a returned value from a hook method.
        byte[] findPoolResult;
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

        JAssistMethodData checkSaneAmounts = new JAssistMethodData(creationEntry,
                "(Lcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/ItemTemplate;Lcom/wurmonline/server/creatures/Creature;Z)V",
                "checkSaneAmounts");
        findReplaceCodeIterator(checkSaneAmounts.getCodeIterator(), find, replace);


        int[] successes = new int[1];
        Arrays.fill(successes, 0);
        checkSaneAmounts.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getObjectCreated", methodCall.getMethodName())){
                    methodCall.replace("$_ = com.Joedobo27.bigcontainers.BigContainersBAEAMod.checkSaneAmountsExceptionsHook( $0.getObjectCreated(), sourceMax, targetMax);");
                    logger.log(Level.FINE, "CreationEntry.class, checkSaneAmounts(), installed hook at line: " + methodCall.getLineNumber());
                    successes[0] = 1;
                }
            }
        });

        boolean changesSuccessful = !Arrays.stream(successes).anyMatch(value -> value == 0);
        if (changesSuccessful) {
            logger.log(Level.INFO, "makeItemsCombine option changes SUCCESSFUL");
        } else {
            logger.log(Level.INFO, "makeItemsCombine option changes FAILURE");
            logger.log(Level.FINE, Arrays.toString(successes));
        }
    }


    private static void unlimitedSpaceBytecode() throws NotFoundException, CannotCompileException{
        if (!unlimitedSpace)
            return;

        int[] successes = new int[16];
        Arrays.fill(successes, 0);

        // Alter Item.hasSpaceFor() so it always returns true.
        itemClass.getCtClass().getMethod("hasSpaceFor", "(I)Z").setBody("return true;");

        // Alter CargoTransportationMethods.targetCanNotBeInsertedCheck() so the volume and x,y,z measurements return max int.
        CtMethod ctmTargetCanNotBeInsertedCheck = cargoTransportationMethods.getCtClass().getDeclaredMethod("targetCanNotBeInsertedCheck");


        ctmTargetCanNotBeInsertedCheck.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getFreeVolume", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "targetCanNotBeInsertedCheck v: " + methodCall.getLineNumber());
                    successes[0] = 1;

                }
                else if (Objects.equals("getContainerSizeX", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "targetCanNotBeInsertedCheck x: " + methodCall.getLineNumber());
                    successes[1] = 1;
                }
                else if (Objects.equals("getContainerSizeY", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "targetCanNotBeInsertedCheck y: " + methodCall.getLineNumber());
                    successes[2] = 1;
                }
                else if (Objects.equals("getContainerSizeZ", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "targetCanNotBeInsertedCheck z: " + methodCall.getLineNumber());
                    successes[3] = 1;
                }
            }
        });

        CtMethod ctmDig = terraforming.getCtClass().getDeclaredMethod("dig");
        ctmDig.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getFreeVolume", methodCall.getMethodName())){
                    methodCall.replace("$_ = 1001;");
                    logger.log(Level.FINE, "dig v: " + methodCall.getLineNumber());
                    successes[4] = 1;
                }
            }
        });

        // Alter Item.insertItem() so the volume and x,y,z size fetching methods all return max int.
        CtMethod ctmInsertItem = itemClass.getCtClass().getMethod("insertItem", "(Lcom/wurmonline/server/items/Item;Z)Z");
        ctmInsertItem.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getFreeVolume", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;;");
                    logger.log(Level.FINE, "insertItem v: " + methodCall.getLineNumber());
                    successes[5] = 1;
                }
                else if (Objects.equals("getContainerSizeX", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "insertItem x: " + methodCall.getLineNumber());
                    successes[6] = 1;
                }
                else if (Objects.equals("getContainerSizeY", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "insertItem y: " + methodCall.getLineNumber());
                    successes[7] = 1;
                }
                else if (Objects.equals("getContainerSizeZ", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "insertItem z: " + methodCall.getLineNumber());
                    successes[8] = 1;
                }
            }
        });

        // About 2780 in bytecode
        // Alter Item.moveToItem() so all the x,y,z size fetching methods all return max int.
        // note that changing the return value will alter indexOfBytecode() results for things afterwards. It may not match javap.exe.
        CtMethod ctmMoveToItem = itemClass.getCtClass().getMethod("moveToItem", "(Lcom/wurmonline/server/creatures/Creature;JZ)Z");
        ctmMoveToItem.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getSizeX", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 2780){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "moveToItem x: " + methodCall.getLineNumber());
                    successes[9] = 1;
                }
                else if (Objects.equals("getSizeY", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 2801){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "moveToItem y: " + methodCall.getLineNumber());
                    successes[10] = 1;
                }
                else if (Objects.equals("getSizeZ", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 2822) {
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "moveToItem z: " + methodCall.getLineNumber());
                    successes[11] = 1;
                }
                //else if (Objects.equals("testInsertItem", methodCall.getClassName())){
                //    methodCall.replace("$_ = true;");
                //    logger.log(Level.FINE, "moveToItem testInsertItem: " + methodCall.getLineNumber());
                //}
            }
        });

        //Alter Item.testInsertHollowItem() so volume and measurements return max int.
        CtMethod ctmTestInsertHollowItem = itemClass.getCtClass().getDeclaredMethod("testInsertHollowItem");
        ctmTestInsertHollowItem.instrument(new ExprEditor() {
           @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getFreeVolume", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "testInsertHollowItem v: " + methodCall.getLineNumber());
                    successes[12] = 1;
                }
                else if (Objects.equals("getContainerSizeX", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "testInsertHollowItem x: " + methodCall.getLineNumber());
                    successes[13] = 1;
                }
                else if (Objects.equals("getContainerSizeY", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "testInsertHollowItem y: " + methodCall.getLineNumber());
                    successes[14] = 1;
                }
                else if (Objects.equals("getContainerSizeZ", methodCall.getMethodName())){
                    methodCall.replace("$_ = java.lang.Integer.MAX_VALUE;");
                    logger.log(Level.FINE, "testInsertHollowItem z: " + methodCall.getLineNumber());
                    successes[15] = 1;
                }
            }
        });
        boolean changesSuccessful = !Arrays.stream(successes).anyMatch(value -> value == 0);
        if (changesSuccessful) {
            logger.log(Level.INFO, "unlimitedSpace option changes SUCCESSFUL");
        } else {
            logger.log(Level.INFO, "unlimitedSpace option changes FAILURE");
            logger.log(Level.FINE, Arrays.toString(successes));
        }
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
            Boolean onePerTile = null;
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

    /**
     * Returning 73 serves as a way to disable certain code. This method is used as a expression editor hook to replace
     * the returned value of this.objectCreated.
     * if (template.isCombine() && this.objectCreated != 73) {
     *
     * @param check int value, which is an entry from ItemList.
     * @return return an int.
     */
    public static int checkSaneAmountsExceptionsHook(int check, int sourceMax, int targetMax){
        int[] exceptions = {ItemList.woad, ItemList.dyeBlue, ItemList.acorn, ItemList.tannin,
                ItemList.cochineal, ItemList.dyeRed, ItemList.dye, ItemList.lye};
        boolean isLargeSizeDifferential = Arrays.stream(exceptions).anyMatch(value -> check == value);
        if (isLargeSizeDifferential) {
            return 73;
        }else{
            return check;
        }
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

    private static int getFieldValue(String fieldName, String className) throws NotFoundException, ClassNotFoundException, IllegalAccessException {
        Class clazz = Class.forName(className);
        Field[] fields = clazz.getFields();
        for (Field a : fields){
            if( Objects.equals(fieldName, a.getName())){
                return (int)a.get(null);
            }
        }
        return 0;
    }
}