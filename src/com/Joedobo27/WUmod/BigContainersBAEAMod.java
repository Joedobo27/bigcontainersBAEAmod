package com.Joedobo27.WUmod;

import com.wurmonline.server.items.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


@SuppressWarnings("UnusedAssignment")
public class BigContainersBAEAMod implements WurmServerMod, ServerStartedListener, Configurable {

    private static boolean makeContainersBig = false;
    private static boolean removeOnePerTileLimits = false;
    private static boolean resizePelt = false;
    private static boolean makeItemsBulk = false;
    private static boolean makeItemsCombine = false;
    private static boolean removeInsideOutsideLimits = false;
    private static Logger logger = Logger.getLogger(BigContainersBAEAMod.class.getName());

    @Override
    public void configure(Properties properties) {
        makeContainersBig = Boolean.valueOf(properties.getProperty("makeContainersBig", Boolean.toString(makeContainersBig)));
        removeOnePerTileLimits = Boolean.valueOf(properties.getProperty("removeOnePerTileLimits", Boolean.toString(removeOnePerTileLimits)));
        resizePelt = Boolean.valueOf(properties.getProperty("resizePelt", Boolean.toString(resizePelt)));
        makeItemsBulk = Boolean.valueOf(properties.getProperty("makeItemsBulk", Boolean.toString(makeItemsBulk)));
        makeItemsCombine = Boolean.valueOf(properties.getProperty("makeItemsCombine", Boolean.toString(makeItemsCombine)));
        removeInsideOutsideLimits = Boolean.valueOf(properties.getProperty("removeInsideOutsideLimits", Boolean.toString(removeInsideOutsideLimits)));
    }

    @Override
    public void onServerStarted() {
        ArrayList<Boolean> optionSwitches = new ArrayList<>(Arrays.asList(makeContainersBig, removeOnePerTileLimits,
                resizePelt, makeItemsBulk, makeItemsCombine, removeInsideOutsideLimits));
        ArrayList<Integer> bigLiquidHolders = new ArrayList<>(Arrays.asList(ItemList.barrelHuge, ItemList.barrelLarge, ItemList.stoneFountain,
                ItemList.stoneFountain2));
        ArrayList<Integer> makeBulk = new ArrayList<>(Arrays.asList(ItemList.logHuge, ItemList.pelt, ItemList.saddle, ItemList.stoneKeystone,
                ItemList.marbleKeystone, ItemList.fishingHookWood, ItemList.fishingHookIron, ItemList.fishingHookWoodAndString,
                ItemList.fishingHookIronAndString )); // in addition by default all items of type fish or gem.
        ArrayList<Integer> makeCombine = new ArrayList<>(Arrays.asList(ItemList.cochineal, ItemList.woad, ItemList.acorn));

        try {
            int makeContainersBigCount = makeContainersBigReflection(optionSwitches, bigLiquidHolders);
            int removeOnePerTileLimitsCount = removeOnePerTileLimitsReflection(optionSwitches);
            resizePeltReflection(optionSwitches);
            int makeItemsBulkCount = makeItemsBulkReflection(optionSwitches, makeBulk);
            int makeItemsCombineCount = makeItemsCombineReflection(optionSwitches, makeCombine);
            int removeInsideOutsideLimitsCount = removeInsideOutsideLimitsReflection(optionSwitches);

            logger.log(Level.INFO, "Number of Containers maxed to 1,728,000 L is " + Integer.toString(makeContainersBigCount));
            logger.log(Level.INFO, "Number of one per tile restrictions removed is " + Integer.toString(removeOnePerTileLimitsCount));
            logger.log(Level.INFO, "Number of items marked as bulk is " + Integer.toString(makeItemsBulkCount));
            logger.log(Level.INFO, "Number of items marked as combine is " + Integer.toString(makeItemsCombineCount));
            logger.log(Level.INFO, "Number of inside or outside only flags removed is " + Integer.toString(removeInsideOutsideLimitsCount));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static int makeContainersBigReflection(ArrayList<Boolean> optionSwitches, ArrayList<Integer> makeBig) throws NoSuchFieldException, IllegalAccessException {
        int makeContainersBigCount = 0;
        if (!optionSwitches.get(0))
            return makeContainersBigCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldUsesSpecifiedContainerSizes = ReflectionUtil.getField(ItemTemplate.class, "usesSpecifiedContainerSizes");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            if (template.isHollow() && (!template.isContainerLiquid() || makeBig.contains(itemId))) {
                if (!template.usesSpecifiedContainerSizes())
                    ReflectionUtil.setPrivateField(template, fieldUsesSpecifiedContainerSizes, Boolean.TRUE);
                template.setContainerSize(1200, 1200, 1200);
                makeContainersBigCount++;
            }
        }
        return makeContainersBigCount;
    }

    private static int removeOnePerTileLimitsReflection(ArrayList<Boolean> optionSwitches) throws NoSuchFieldException, IllegalAccessException {
        int removeOnePerTileLimitsCount = 0;
        if (!optionSwitches.get(1))
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

    private static void resizePeltReflection(ArrayList<Boolean> optionSwitches) throws NoSuchFieldException, IllegalAccessException {
        if (!optionSwitches.get(2))
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

    private static int makeItemsBulkReflection(ArrayList<Boolean> optionSwitches, ArrayList<Integer> makeBulk) throws NoSuchFieldException, IllegalAccessException {
        int makeItemsBulkCount = 0;
        if (!optionSwitches.get(3))
            return makeItemsBulkCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldGem = ReflectionUtil.getField(ItemTemplate.class, "gem");
        Field fieldBulk = ReflectionUtil.getField(ItemTemplate.class, "bulk");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            Boolean isGem = ReflectionUtil.getPrivateField(template, fieldGem);
            if (template.isFish() || isGem || makeBulk.contains(itemId)) {
                ReflectionUtil.setPrivateField(template, fieldBulk, Boolean.TRUE);
                makeItemsBulkCount++;
            }
        }
        return makeItemsBulkCount;
    }

    private static int makeItemsCombineReflection(ArrayList<Boolean> optionSwitches, ArrayList<Integer> makeCombine) throws NoSuchFieldException, IllegalAccessException {
        int makeItemsCombineCount = 0;
        if (!optionSwitches.get(4))
            return makeItemsCombineCount;
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        Field fieldCombine = ReflectionUtil.getField(ItemTemplate.class, "combine");
        for (ItemTemplate template : fieldTemplates.values()) {
            Integer itemId = template.getTemplateId();
            if (makeCombine.contains(itemId)) {
                ReflectionUtil.setPrivateField(template, fieldCombine, Boolean.TRUE);
                makeItemsCombineCount++;
            }
        }
        if (aaaJoeCommon.modifiedCheckSaneAmounts) {
            ArrayList<Integer> abc = ReflectionUtil.getPrivateField(CreationEntry.class, ReflectionUtil.getField(CreationEntry.class,
                    "largeMaterialRatioDifferentials"));
            ArrayList<Integer> b = new ArrayList<>(Arrays.asList(ItemList.woad, ItemList.dyeBlue, ItemList.acorn, ItemList.tannin,
                    ItemList.cochineal, ItemList.dyeRed, ItemList.dye));
            b.stream().filter(a -> !abc.contains(a)).forEach(abc::add);
        }
        return makeItemsCombineCount;
    }

    private static int removeInsideOutsideLimitsReflection(ArrayList<Boolean> optionSwitches) throws NoSuchFieldException, IllegalAccessException {
        int removeInsideOutsideLimitsCount = 0;
        if (!optionSwitches.get(5))
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
}