package com.Joedobo27.WUmod;

import com.wurmonline.server.items.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmMod;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BigContainersBAEAMod implements WurmMod, Initable, ServerStartedListener, Configurable, ItemTypes {

    private boolean makeContainersBig = false;
    private boolean removeOnePerTileLimits = false;
    private boolean resizePelt = false;
    private boolean makeItemsBulk = false;
    private boolean makeItemsCombine = false;
    private boolean removeInsideOutsideLimits = false;
    private Logger logger = Logger.getLogger(this.getClass().getName());

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

        int makeContainersBigCount = 0;
        int removeOnePerTileLimitsCount = 0;
        int makeItemsBulkCount = 0;
        int makeItemsCombineCount = 0;
        int removeInsideOutsideLimitsCount = 0;

        try {
            //<editor-fold desc="Get Fields for reflection">
            Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(
                        ItemTemplateFactory.class, ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
            Field fieldOnePerTile = ReflectionUtil.getField(ItemTemplate.class, "onePerTile");
            Field fieldUsesSpecifiedContainerSizes = ReflectionUtil.getField(ItemTemplate.class, "usesSpecifiedContainerSizes");
            Field fieldIsTransportable = ReflectionUtil.getField(ItemTemplate.class, "isTransportable");
            Field fieldCentimetersX = ReflectionUtil.getField(ItemTemplate.class, "centimetersX");
            Field fieldCentimetersY = ReflectionUtil.getField(ItemTemplate.class, "centimetersY");
            Field fieldCentimetersZ = ReflectionUtil.getField(ItemTemplate.class, "centimetersZ");
            Field fieldWeight = ReflectionUtil.getField(ItemTemplate.class, "weight");
            Field fieldBulk = ReflectionUtil.getField(ItemTemplate.class, "bulk");
            Field fieldCombine = ReflectionUtil.getField(ItemTemplate.class, "combine");
            Field fieldOutsideonly = ReflectionUtil.getField(ItemTemplate.class, "outsideonly");
            Field fieldInsideOnly = ReflectionUtil.getField(ItemTemplate.class, "insideOnly");
            Field fieldUseOnGroundOnly = ReflectionUtil.getField(ItemTemplate.class, "useOnGroundOnly");
            //</editor-fold>

            // ******************** start iteration through all the templates******************************
            for (ItemTemplate template : fieldTemplates.values()) {
                // *******************Set internal sizes big for select templates.*************************
                Integer itemId = template.getTemplateId();
                if (makeContainersBig) {
                    if (template.isHollow() && (!template.isContainerLiquid() || itemId == ItemList.barrelHuge || itemId == ItemList.barrelLarge ||
                            itemId == ItemList.stoneFountain || itemId == ItemList.stoneFountain2 || itemId == ItemList.amphoraLargePottery)) {
                        if (!template.usesSpecifiedContainerSizes()) {
                            ReflectionUtil.setPrivateField(template, fieldUsesSpecifiedContainerSizes, Boolean.TRUE);
                        }
                        template.setContainerSize(1200, 1200, 1200);
                        makeContainersBigCount++;
                    }
                }
                //*********Remove one per tile restriction.*****************
                if (removeOnePerTileLimits) {

                    Boolean onePerTile;
                    onePerTile = ReflectionUtil.getPrivateField(template, fieldOnePerTile);
                    if (onePerTile) {
                        ReflectionUtil.setPrivateField(template, fieldOnePerTile, Boolean.FALSE);
                        removeOnePerTileLimitsCount++;
                    }
                }
                /*
                //***************Add Transportable flag to select templates**********************
                if (itemId == STONE_ALTAR || itemId == TRASH_HEAP || itemId == ROWING_BOAT || itemId == WOOD_ALTAR
                        || itemId == SILVER_ALTAR || itemId == GOLD_ALTAR) {
                    if (!template.isTransportable()) {
                        ReflectionUtil.setPrivateField(template, fieldIsTransportable, Boolean.TRUE);
                    }
                }
                */
                //*****************redo size & weight for select templates**********************
                if (resizePelt) {
                    if (itemId == ItemList.pelt) {
                        ReflectionUtil.setPrivateField(template, fieldCentimetersX, 10);
                        ReflectionUtil.setPrivateField(template, fieldCentimetersY, 10);
                        ReflectionUtil.setPrivateField(template, fieldCentimetersZ, 10);
                        ReflectionUtil.setPrivateField(template, fieldWeight, 100);
                        logger.log(Level.INFO, "Pelt resized.");
                    }
                }
                //****************add bulk flag to select templates***********************
                if (makeItemsBulk) {
                    if (itemId == ItemList.logHuge || itemId == ItemList.pelt || itemId == ItemList.saddle || itemId == ItemList.stoneKeystone ||
                            itemId == ItemList.marbleKeystone || template.isFish() || itemId == ItemList.fishingHookWood ||
                            itemId == ItemList.fishingHookIron || itemId == ItemList.fishingHookWoodAndString ||
                            itemId == ItemList.fishingHookIronAndString) {
                        ReflectionUtil.setPrivateField(template, fieldBulk, Boolean.TRUE);
                        makeItemsBulkCount++;
                    }
                }
                //***************add combine flags to select templates*********************
                if (makeItemsCombine) {
                    if (itemId == ItemList.cochineal || itemId == ItemList.woad || itemId == ItemList.acorn) {
                        ReflectionUtil.setPrivateField(template, fieldCombine, Boolean.TRUE);
                        makeItemsCombineCount++;
                    }
                }
                //*****************remove outside and inside only flags from all**********************
                if (removeInsideOutsideLimits) {
                    Boolean insideOnly;
                    insideOnly = ReflectionUtil.getPrivateField(template, fieldInsideOnly);
                    if (template.isOutsideOnly() || insideOnly) {
                        ReflectionUtil.setPrivateField(template, fieldOutsideonly, Boolean.FALSE);
                        ReflectionUtil.setPrivateField(template, fieldInsideOnly, Boolean.FALSE);
                        removeInsideOutsideLimitsCount++;
                    }
                }
                //*****************************remove ground only flags***************************
                /*
                if (itemId == BSB || itemId == FSB){
                    if (template.isUseOnGroundOnly()) {
                        ReflectionUtil.setPrivateField(template, fieldUseOnGroundOnly, Boolean.FALSE);
                    }
                }
                */
            }
            logger.log(Level.INFO, "Number of Containers maxed to 1,728,000 L is " + Integer.toString(makeContainersBigCount));
            logger.log(Level.INFO, "Number of one per tile restrictions removed is " + Integer.toString(removeOnePerTileLimitsCount));
            logger.log(Level.INFO, "Number of items marked as bulk is " + Integer.toString(makeItemsBulkCount));
            logger.log(Level.INFO, "Number of items marked as combine is " + Integer.toString(makeItemsCombineCount));
            logger.log(Level.INFO, "Number of inside or outside only flags removed is " + Integer.toString(removeInsideOutsideLimitsCount));

        }catch (IllegalAccessException | NoSuchFieldException e){
            logger.log(Level.WARNING, e.toString());
        }
    }

    @Override
    public void init() {
    }
}