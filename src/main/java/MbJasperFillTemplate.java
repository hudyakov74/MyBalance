import lsfusion.base.file.FileData;
import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import net.sf.jasperreports.crosstabs.*;
import net.sf.jasperreports.crosstabs.design.*;
import net.sf.jasperreports.crosstabs.type.CrosstabColumnPositionEnum;
import net.sf.jasperreports.crosstabs.type.CrosstabRowPositionEnum;
import net.sf.jasperreports.crosstabs.type.CrosstabTotalPositionEnum;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRStyle;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.type.*;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JRXmlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.awt.*;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;


public class MbJasperFillTemplate  extends InternalAction {

    private static final Logger log = LoggerFactory.getLogger(MbJasperFillTemplate.class);

    public MbJasperFillTemplate(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }
    String formatTextExpr(String name) {
        return "\"".concat(name).concat("\"");
    }
    String formatFieldExpr(String name) {
        return "$F{".concat(name).concat("}");
    }
    String formatValueExpr(String name) {
        return "$V{".concat(name).concat("}");
    }

    void addFields(List<String> in, List<JRField> fieldsList, String prefix) {
        for (String name: in) {
            if (fieldsList.stream().filter(a -> a.getName().equals(prefix.concat(name))).findAny().isEmpty()) {
                JRDesignField f = new JRDesignField();
                f.setName(prefix.concat(name));
                f.setValueClassName("java.lang.String");// f.setValueClass(String.class);
                fieldsList.add(f);
            }
        }
    }

    void addField( String name, String prefix, String valueClassName, List<JRField> fieldsList) {
        if (fieldsList.stream().filter(a -> a.getName().equals(prefix.concat(name))).findAny().isEmpty()) {
            JRDesignField f = new JRDesignField();
            f.setName( prefix.concat(name) );
            f.setValueClassName(valueClassName); // f.setValueClassName("java.math.BigDecimal");
            fieldsList.add(f);
        }
    }

    void addMeasures(List<String> in, JRDesignCrosstab crossTab) {
        for (String name: in) {
            if (crossTab.getMesuresList().stream().filter(a -> a.getName().equals(name)).findAny().isEmpty()) {
            JRDesignCrosstabMeasure f = new JRDesignCrosstabMeasure();
            f.setCalculation(CalculationEnum.NOTHING);
            f.setValueClassName("java.lang.String");
            f.setName(name);
            f.setValueExpression(new JRDesignExpression(formatFieldExpr(name)));
            crossTab.getMesuresList().add(f);
            crossTab.getMeasureIndicesMap().put(name,crossTab.getMeasureIndicesMap().size()); // скорей всего надо - но что c индексом?
        }
        }
    }

    void addMeasure(String name, JRDesignCrosstab crossTab, CalculationEnum cEnum, String className, String fieldExpr) {
        if (crossTab.getMesuresList().stream().filter(a -> a.getName().equals(name)).findAny().isEmpty()) {
        JRDesignCrosstabMeasure f = new JRDesignCrosstabMeasure();
        f.setCalculation(cEnum);
        f.setValueClassName(className); //"java.lang.String");
        f.setName(name);
        f.setValueExpression(new JRDesignExpression(fieldExpr)); //formatFieldExpr(name)
        crossTab.getMesuresList().add(f);
        crossTab.getMeasureIndicesMap().put(name,crossTab.getMeasureIndicesMap().size()); // скорей всего надо - но что c индексом?
    }
    }


    void addMeasureSumBigDecimal(String name, JRDesignCrosstab crossTab, String className, String divider) {
        JRDesignCrosstabMeasure f = new JRDesignCrosstabMeasure();
        f.setCalculation(CalculationEnum.SUM);
        f.setValueClassName(className);
        f.setName(name);
        if (divider.length() > 1 ) {
            f.setValueExpression(new JRDesignExpression(formatFieldExpr(name).concat(".divide(BigDecimal.valueOf(").concat(divider).concat("), 6, RoundingMode.HALF_UP)")));
        }
        else  {
            f.setValueExpression(new JRDesignExpression(formatFieldExpr(name)));
        }

        crossTab.getMesuresList().add(f);
        crossTab.getMeasureIndicesMap().put(name,crossTab.getMeasureIndicesMap().size()); // скорей всего надо - но что c индексом?
    }

    // todo !!! тут вызывать генерацию групп не с полей первичных а используем сгенерированные выражения
    void addRowGroup(List<String> in, JRDesignCrosstab crossTab) {
        int vWidth = 320; // ширина 1 колонки - потом можно будет   и дополнять
        int vHeight = 13;
        // одна группа всегда существует
        JRDesignCrosstabRowGroup prevJRCrosstabRowGroup = null;

        // JRDesignCrosstabRowGroup rg = (JRDesignCrosstabRowGroup) Arrays.stream(crossTab.getRowGroups()).toList().getFirst();
        for (int i = 0; i < in.size(); i++) {
            JRDesignCrosstabRowGroup nGroup = new JRDesignCrosstabRowGroup();
            crossTab.getRowGroupsList().add(nGroup);
            // преднастройки остальные группы
            if (i == 0) {
                nGroup.setTotalPosition(CrosstabTotalPositionEnum.END);
            } else {
                nGroup.setTotalPosition(CrosstabTotalPositionEnum.START);
            }
            // nGroup.setPosition(CrosstabRowPositionEnum.MIDDLE );
            nGroup.setName("gv_".concat(in.get(i))); // todo !!! тут автоименовать
            crossTab.getRowGroupIndicesMap().put(nGroup.getName(), i);
            JRDesignCrosstabBucket nBucket = new JRDesignCrosstabBucket();
            nBucket.setValueClassName("java.lang.String");
            nGroup.setBucket(nBucket);
            // переменная группировки
            JRDesignExpressionChunk nChunk = new JRDesignExpressionChunk();
            nBucket.setExpression(new JRDesignExpression(formatFieldExpr(in.get(i)))); // todo !!! тут вставлять сгенерированное имя с условиями из разных полей
            prevJRCrosstabRowGroup = nGroup;
            // преднастройки для всех
            nGroup.setWidth(0);
            ((JRDesignCellContents) nGroup.getTotalHeader()).setHeight(vHeight);
            //((JRDesignCellContents) rg.getTotalHeader()).
            //----------------------
        }
        // преднастройки ПОСЛЕДНЯЯ группа
        if (prevJRCrosstabRowGroup  != null) {
            prevJRCrosstabRowGroup.setWidth(vWidth);
        }
        //((JRDesignCellContents) prevJRCrosstabRowGroup.getHeader()).setHeight(vHeight);
    }

    void addColumnGroup(List<String> in, JRDesignCrosstab crossTab, int crosstabWoHeader) {
        int vHeight = 13;
        crossTab.getColumnGroupIndicesMap().clear();

        for (int i = 0; i < in.size(); i++) {
            JRDesignCrosstabColumnGroup nGroup = new JRDesignCrosstabColumnGroup();
            crossTab.getColumnGroupsList().add(nGroup);
            if (crosstabWoHeader > 0) {
                nGroup.setHeight(0);
            } else {
                nGroup.setHeight(vHeight);
            }
            nGroup.setPosition(CrosstabColumnPositionEnum.STRETCH);
            if (i == 0) {
                nGroup.setTotalPosition(CrosstabTotalPositionEnum.END);
            } else {
                nGroup.setTotalPosition(CrosstabTotalPositionEnum.START);   //prevJRCrosstabColGroup.getTotalPositionValue());
            }
            nGroup.setName("gh_".concat(in.get(i))); //firstJRCrosstabColGroup.getName().concat(Integer.toString(i)));

            crossTab.getColumnGroupIndicesMap().put(nGroup.getName(), i);
            JRDesignCrosstabBucket nBucket = new JRDesignCrosstabBucket();
            nGroup.setBucket(nBucket);
            nBucket.setValueClassName("java.lang.String");
            // переменная группировки
            JRDesignExpressionChunk nChunk = new JRDesignExpressionChunk();
            nBucket.setExpression(new JRDesignExpression(formatFieldExpr(in.get(i))));
        }
    }

    void fillColumnsHeader(String grName, JasperDesign design, List<String> colCellHeaderNameStr, List<String>   colCellHeaderNameUnitStr, List<Integer> indexColumnData, List<String>  dockToPreviuosHeader, List<String> dockToPreviuosHeaderText,
                           int cellWidth_, JRDesignCellContents colCont,int height, int level, String summaryName
            ,Map<String, Map<Integer, Integer>> colColumsCount
    ) {

        int nWidth = 0;

        for (Integer  ix:indexColumnData) {
            // 10 код + 30 имя + (20) на уровень - 1

            if (colColumsCount.get(grName) != null && colColumsCount.get(grName).get(ix) != null) {
                cellWidth_ = colColumsCount.get(grName).get(ix);
            }
            else {
                continue;
            }

            String nameCol = colCellHeaderNameStr.get(ix);
            if ( colCellHeaderNameUnitStr.get(ix).length()>1 ) {
                nameCol = nameCol.concat(" ,").concat(colCellHeaderNameUnitStr.get(ix));
            }

            JRDesignTextField nf = new JRDesignTextField();
            nf.setHeight(10);
            nf.setX(cellWidth_ * nWidth);
            nf.setY(height - 10);
            nf.setWidth(cellWidth_);
            nf.setExpression(new JRDesignExpression(  Integer.toString(level)  ));
            nf.setTextAdjust(TextAdjustEnum.SCALE_FONT);
            nf.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            nf.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            nf.setFontSize(6f);
            nf.getLineBox().getPen().setLineWidth(0.25f);
            //nf.getLineBox().getLeftPen().setLineWidth();
            colCont.getChildren().add(nf);


            nf = new JRDesignTextField();
            if (dockToPreviuosHeader.get(ix).equals("-")) {
                nf.setHeight(20); // тут можно бокс в т.ч. воткнуть!! если предыдущая не "-" без рамок
                nf.setY(height - 30 - 10 + 10);

                {   JRDesignTextField nf1 = new JRDesignTextField();
                    nf1.setX(cellWidth_ * nWidth);
                    nf1.setY(height - 30 - 10);
                    nf1.setHeight(10);
                    nf1.setWidth(cellWidth_);
                    nf1.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
                    nf1.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
                    if (dockToPreviuosHeaderText.get(ix).length() > 1 ) // или есть явный текст для вывода
                    {   // выводим если текс точно не совпадает с пред столбцом иначе оно соединяется просто
                        if (ix >= 1 && !dockToPreviuosHeaderText.get(ix-1).equals(dockToPreviuosHeaderText.get(ix))  ) {
                            nf1.setExpression(new JRDesignExpression(formatTextExpr(dockToPreviuosHeaderText.get(ix))));
                            nf1.getLineBox().getLeftPen().setLineWidth(0.25f);  // отделим палочкой если была отметка в прошлом столбце
                        }
                    }
                    else if ((ix == 0  // или колонка первая - зачем не знаю первой быть в т.ч.
                            || ix >= 1 // или предыдущая не в т.ч чтобы не повторялось
                            && dockToPreviuosHeader.get(ix-1).equals("+"))
                    ) {
                        nf1.setExpression(new JRDesignExpression(formatTextExpr("в т.ч.")));
                    } // else без текста
                    nf1.setTextAdjust(TextAdjustEnum.SCALE_FONT);
                    nf1.setFontSize(8f);
                    nf1.setBlankWhenNull(true);
                    colCont.getChildren().add(nf1);
                }
            }
            else {
                nf.setHeight(30);
                nf.setY(height - 30 - 10);
            }
            nf.setX(cellWidth_ * nWidth);

            nf.setWidth(cellWidth_);
            nf.setExpression(new JRDesignExpression(formatTextExpr(nameCol)));
            nf.setTextAdjust(TextAdjustEnum.SCALE_FONT);
            nf.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            nf.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            nf.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            nf.setMode(ModeEnum.OPAQUE);
            nf.setStyle( Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findAny().get() );
            nf.setFontSize(8f);
            nf.getLineBox().getPen().setLineWidth(0.25f);
            colCont.getChildren().add(nf);
            nWidth++;
        }
        if (colColumsCount.get(grName)!= null) {
            nWidth = colColumsCount.get(grName).size();
        }
        //И заголовок ИТОГО ПО
        if (summaryName != null) {
            JRDesignTextField nf = new JRDesignTextField();
            nf.setHeight(height - 30 - 10);
            nf.setX(0);
            nf.setY(0);
            nf.setWidth(cellWidth_ * nWidth);
            nf.setExpression(new JRDesignExpression(summaryName));
            nf.setTextAdjust(TextAdjustEnum.SCALE_FONT);
            nf.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            nf.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            nf.setMode(ModeEnum.OPAQUE);
            nf.setStyle(Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findAny().get());
            nf.setFontSize(12f);
            nf.getLineBox().getPen().setLineWidth(0.25f);
            colCont.getChildren().add(nf);
        }
    }


    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        FileData fileIn = (FileData) getParam(0, context);
        int modeTemplate = (int) getParam(1, context);
        List<String> rowGroupCodeStr = Arrays.asList(((String) getParam(2, context)).split(","));
        List<String> rowGroupNameStr = Arrays.asList(((String) getParam(3, context)).split(","));
        List<String> rowGroupStyleStr = Arrays.asList(((String) getParam(4, context)).split(","));
        List<String> rowGroupLevel    = Arrays.asList(((String) getParam(5, context)).split(","));
        List<String> colGroupCodeStr = Arrays.asList(((String) getParam(6, context)).split(","));
        List<String> colGroupNameStr = Arrays.asList(((String) getParam(7, context)).split(","));
        String colColumnEnabledStr = (String) getParam(8, context);
        // тут пустые значения - 1 пробел.
        List<String> colCellHeaderNameStr        =   Arrays.asList(((String) getParam(9, context)).split("\\|,\\|"));
        List<String> colCellHeaderNameUnitStr    =   Arrays.asList(((String) getParam(10, context)).split("\\|,\\|"));
        List<String> colCellHeaderNameRatioStr   =   Arrays.asList(((String) getParam(11, context)).split("\\|,\\|"));


        List<String> colCellMesuareFunctionStr   =   Arrays.asList(((String) getParam(12, context)).split("\\|,\\|"));
        List<String> colCellExpression           =   Arrays.asList(((String) getParam(13, context)).split("\\|,\\|"));
        List<String> colCellFormat               =   Arrays.asList(((String) getParam(14, context)).split("\\|,\\|"));
        List<String> colCellType                 =   Arrays.asList(((String) getParam(15, context)).split("\\|,\\|"));
        List<String> dockToPreviuosHeader        =   Arrays.asList(((String) getParam(16, context)).split("\\|,\\|"));
        List<String> dockToPreviuosHeaderText    =   Arrays.asList(((String) getParam(17, context)).split("\\|,\\|"));
        List<String> compareToBaseSub            =   Arrays.asList(((String) getParam(18, context)).split("\\|,\\|"));
        List<String> reqired =   Arrays.asList(((String) getParam(19, context)).split("\\|,\\|"));
        List<String> disableTotalByH             =   Arrays.asList(((String) getParam(20, context)).split(","));
        List<String> totalCompareH               =   Arrays.asList(((String) getParam(21, context)).split(","));
        String labelCrosstab                     =   (String) getParam(22, context);
        Long idPartition                         =   (Long) getParam(23, context);
        int crosstabWoHeader                     =   (int) getParam(24, context);
        int crosstabWoSummary                    =   (int) getParam(25, context);

        try {
            JasperDesign design = JRXmlLoader.load(fileIn.getRawFile().getInputStream());
            List<JRField> fieldsList = design.getMainDesignDataset().getFieldsList();
            //1 добавим поля колонок и строк
            addField("idPart", "","java.lang.Long", fieldsList); // для разделов в разных кростабах
            addFields(rowGroupCodeStr, fieldsList, "");
            addFields(rowGroupNameStr, fieldsList, "");
            addFields(rowGroupStyleStr, fieldsList, "style");
            addFields(colGroupCodeStr, fieldsList, "");
            addFields(colGroupNameStr, fieldsList, "");
            // в Summary Единственный и первый попавшийся кростаб - или по label
            JRDesignCrosstab crosstab;
            if (labelCrosstab.isEmpty()) {
                crosstab = (JRDesignCrosstab) design.getSummary().getChildren().stream().filter(JRDesignCrosstab.class::isInstance).findAny().get();
            } else {
                crosstab = (JRDesignCrosstab) design.getSummary().getChildren().stream()
                        .filter(JRDesignCrosstab.class::isInstance)
                        .filter(a -> ((JRDesignCrosstab)a).getPropertiesMap().getProperty("com.jaspersoft.studio.element.name").equals(labelCrosstab))
                        .findAny()
                        .get();
                // фильтруем данные по разделу
                ((JRDesignCrosstabDataset)crosstab.getDataset()).setIncrementWhenExpression(new JRDesignExpression("$F{idPart} == ".concat(Long.toString(idPartition))));  // idPartition
            }

            if (crosstab != null) {

                addRowGroup(rowGroupCodeStr, crosstab); // todo !!!  сгенерированные выражения выдаем через доп список
                addColumnGroup(colGroupCodeStr, crosstab, crosstabWoHeader);
                addMeasures(rowGroupCodeStr, crosstab);
                addMeasures(rowGroupNameStr, crosstab); //  todo !!! это тоже с выражениями для имен
                addMeasures(colGroupCodeStr, crosstab);
                addMeasures(colGroupNameStr, crosstab);
                ///////////////////////////
                // РАЗДЕЛ ЗАВЕДЕНИЯ ЯЧЕЕК - это пустая инициализация ячеек в группах и итогах по 1 ширине ячейки
                ////////////////////////
                int cellHeight_ = 13;
                int cellWidth_ = 0;
                {   // для   детаил/детаил должна быть  !!!
                    for (JRCrosstabCell cell : crosstab.getCellsList()) {
                        ((JRDesignCrosstabCell) cell).setHeight(cellHeight_);
                        ((JRDesignCrosstabCell) cell).setWidth(cellWidth_);
                        cell.getContents().getPropertiesMap().setProperty("com.jaspersoft.studio.layout", "com.jaspersoft.studio.editor.layout.FreeLayout");
                    }
                    // итоги колонок
                    //getRowTotalGroup
                    for (JRCrosstabColumnGroup col : crosstab.getColumnGroupsList()) {
                        Optional<JRCrosstabCell> cellFind = crosstab.getCellsList().stream().filter(a -> a.getRowTotalGroup() == null && a.getColumnTotalGroup() != null && a.getColumnTotalGroup().equals(col.getName())).findFirst();
                        if (cellFind.isEmpty()) {
                            JRDesignCrosstabCell cell_ = new JRDesignCrosstabCell();
                            //<property name="com.jaspersoft.studio.layout" value="com.jaspersoft.studio.editor.layout.FreeLayout"/>
                            cell_.setColumnTotalGroup(col.getName());
                            cell_.setHeight(cellHeight_);
                            cell_.setWidth(cellWidth_);
                            cell_.getContents().getPropertiesMap().setProperty("com.jaspersoft.studio.layout", "com.jaspersoft.studio.editor.layout.FreeLayout");
                            crosstab.addCell(cell_);
                        } else {
                            //  ((JRDesignCrosstabCell) cellFind.get()).setHeight(cellHeight_ * 3);
                        }
                    }
                    // итоги строк
                    for (JRCrosstabRowGroup row : crosstab.getRowGroupsList()) {
                        if (crosstab.getCellsList().stream().filter(a -> a.getRowTotalGroup() != null && a.getRowTotalGroup().equals(row.getName()) && a.getColumnTotalGroup() == null).findAny().isEmpty()) {
                            JRDesignCrosstabCell cell_ = new JRDesignCrosstabCell();
                            cell_.setRowTotalGroup(row.getName());
                            cell_.setHeight(cellHeight_);
                            cell_.setWidth(cellWidth_);
                            cell_.getContents().getPropertiesMap().setProperty("com.jaspersoft.studio.layout", "com.jaspersoft.studio.editor.layout.FreeLayout");
                            crosstab.addCell(cell_);
                        }
                    }
                    // в пересечениях колонок и строк
                    for (JRCrosstabColumnGroup col : crosstab.getColumnGroupsList()) {
                        for (JRCrosstabRowGroup row : crosstab.getRowGroupsList()) {
                            if (crosstab.getCellsList().stream().filter(a ->
                                    a.getRowTotalGroup() != null
                                            && a.getColumnTotalGroup() != null
                                            && a.getRowTotalGroup().equals(row.getName())
                                            && a.getColumnTotalGroup().equals(col.getName())
                            ).findAny().isEmpty()) {
                                JRDesignCrosstabCell cell_ = new JRDesignCrosstabCell();
                                cell_.setRowTotalGroup(row.getName());
                                cell_.setColumnTotalGroup(col.getName());
                                cell_.setHeight(cellHeight_);
                                cell_.setWidth(cellWidth_);
                                cell_.getContents().getPropertiesMap().setProperty("com.jaspersoft.studio.layout", "com.jaspersoft.studio.editor.layout.FreeLayout");
                                crosstab.addCell(cell_);
                            }
                        }
                    }
                }
                cellWidth_ = 77; //todo настойку надо поднять в отчет
                ///////////////////////////
                // Заполним заголовки групп СТРОК  (3 первых колонки!!!)
                // закидываем в ячейки имена, шрифт и фон из стилей!!!
                // формат ячеек
                // Стили задаются тут вместе с ячейками
                ////////////////////////
                {
                    // пока стандарт такой 3 ячейки в 320 поле
                    // 1. 10 - уровень иерархии
                    // 2. 10 - смещение текста
                    // 3. 300 текст
                    // цикл пока по списку - но может придется сделать по map

                    JRStyle columnIndStyle = new JRDesignStyle();
                    JRCrosstabRowGroup lastJRCrosstabRowGroup = null;
                    int j = 0;
                    for (JRCrosstabRowGroup row : crosstab.getRowGroupsList()) {
                        row.getTotalHeader().getPropertiesMap().setProperty("com.jaspersoft.studio.layout", "com.jaspersoft.studio.editor.layout.FreeLayout");
                        int j_ = j;
                        JRStyle curStyle = null;
                        if (j_ == 0) {
                            curStyle = Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findFirst().get();
                        } else { // заголовки содержат группы предыдущих уровней
                            curStyle = Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG".concat(rowGroupStyleStr.get(j_ - 1)))).findFirst().get();
                        }
                        // ЯЧЕЙКА 1 - уровень иерархии и пометка удаления
                        JRDesignTextField nText = new JRDesignTextField();
                        nText.setHeight(cellHeight_);
                        nText.setStyle(columnIndStyle);
                        nText.setBackcolor(new Color(255,255,200+(byte)modeTemplate)); // передадим индекс кросстаба
                        nText.setWidth(10);

                        if (lastJRCrosstabRowGroup == null) {
                            if (crosstabWoSummary > 0) {
                                nText.setExpression( new JRDesignExpression("-1" ) ); // строка Итого - если не ломать иерархию - то сюда можно записать -1 но лучше стандартно - выключить итоги
                            }
                            else {
                                nText.setExpression(new JRDesignExpression(Integer.toString(j)));
                            }
                        }
                        else {
                            if (rowGroupLevel.get(j_-1).length() > 1) { // если вывод - пустая строка - то уровень отрицательный
                                //nText.setExpression(new JRDesignExpression(Integer.toString(j).concat(rowGroupLevel.get(j_))));  // -
                                nText.setExpression(new JRDesignExpression(
                                        formatValueExpr(rowGroupNameStr.get(j - 1))
                                                .concat("==null")
                                                .concat("?-(")
                                                .concat(Integer.toString(j))
                                                .concat(rowGroupLevel.get(j_-1))
                                                .concat("):(")
                                                .concat(Integer.toString(j))
                                                .concat(rowGroupLevel.get(j_-1))
                                                .concat(")")

                                ));
                            }
                            else {
                                nText.setExpression(new JRDesignExpression(
                                        formatValueExpr(rowGroupNameStr.get(j - 1)).concat("==null").concat("?").concat(Integer.toString(-j)).concat(":").concat(Integer.toString(j))
                                        // ЭТО ОТКЛЮЧЕНИЕ вывода строки
                                        // todo !!! это имя поля по умолчанию обрабатывается - видимо нужен выбор вывода имени
                                        // todo !!! 1. надо ли вообще выводить, 2. какой поле - зависит от имени группы
                                )); //formatValueExpr(rowGroupNameStr.get(j - 1))
                            }
                        }


                        // nText.setStyle(curStyle); // StyleG10
                        nText.setBlankWhenNull(true);
                        nText.setMode(ModeEnum.OPAQUE);
                        nText.getLineBox().getPen().setLineWidth(0.1f);
                        row.getTotalHeader().getChildren().add(nText);

                        // ЯЧЕЙКА 2 - значение смещения табуляции для иерархического значения
                        nText = new JRDesignTextField();
                        nText.setHeight(cellHeight_);
                        nText.setWidth(10);
                        nText.setX(10);

                        if (lastJRCrosstabRowGroup != null && rowGroupLevel.get(j_-1).length() > 1) { //!!! группа ????
                            nText.setExpression(new JRDesignExpression(Integer.toString(j).concat(rowGroupLevel.get(j_-1))));  // -
                        }
                        else {
                            nText.setExpression(new JRDesignExpression(Integer.toString(j)));
                        }

                        nText.setStyle(curStyle); // StyleG10
                        nText.setBlankWhenNull(true);
                        nText.setMode(ModeEnum.OPAQUE);
                        nText.getLineBox().getPen().setLineWidth(0.1f);
                        row.getTotalHeader().getChildren().add(nText);

                        // ЯЧЕЙКА 3 - значение текстовое
                        nText = new JRDesignTextField();
                        nText.setHeight(cellHeight_);
                        nText.setX(20);
                        nText.setWidth(300);
                        if (lastJRCrosstabRowGroup == null) {
                            nText.setExpression(new JRDesignExpression("\"Итого\""));  // первый итог идет вниз
                        } else {
                            nText.setExpression(new JRDesignExpression(formatValueExpr(rowGroupNameStr.get(j - 1))));
                            // ЭТО   вывод строки
                            // todo !!! это имя поля по умолчанию обрабатывается - видимо нужен выбор вывода имени
                            // todo !!! 1. надо ли вообще выводить, 2. какой поле - зависит от имени группы
                        }
                        nText.setStyle(curStyle); // StyleG10
                        nText.setBlankWhenNull(true);
                        nText.setMode(ModeEnum.OPAQUE);
                        nText.getLineBox().getPen().setLineWidth(0.1f);
                        row.getTotalHeader().getChildren().add(nText);

                        j++;
                        lastJRCrosstabRowGroup = row;
                    }

                    // самй нижний уровень - текущая переменная
                    if (lastJRCrosstabRowGroup != null) {
                        int j_ = j - 1;

                        JRDesignTextField nText = new JRDesignTextField();
                        nText.setHeight(cellHeight_);
                        nText.setStyle(columnIndStyle);
                        nText.setBackcolor(new Color(255,255,200+(byte)modeTemplate)); // передадим индекс кросстаба

                        nText.setWidth(10);
                        if (rowGroupLevel.get(j_).length() > 1) { //!!! группа ????
                            nText.setExpression(new JRDesignExpression(Integer.toString(j).concat(rowGroupLevel.get(j_))));  // -
                            //nText.setExpression(new JRDesignExpression(Integer.toString(j)));  // -
                        }
                        else {
                            nText.setExpression(new JRDesignExpression(Integer.toString(j)));  // -
                        }
                        //Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00".concat())).findAny().isEmpty();

                        // nText.setStyle(Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findAny().get()); // StyleG10
                        nText.setBlankWhenNull(true);
                        nText.setMode(ModeEnum.OPAQUE);
                        nText.getLineBox().getPen().setLineWidth(0.1f);
                        lastJRCrosstabRowGroup.getHeader().getChildren().add(nText);


                        nText = new JRDesignTextField();
                        nText.setHeight(cellHeight_);
                        nText.setWidth(10);
                        nText.setX(10);

                        if (rowGroupLevel.get(j_).length()>1) { //!!! группа ????
                            nText.setExpression(new JRDesignExpression(Integer.toString(j).concat(rowGroupLevel.get(j_))));  // -
                            //nText.setExpression(new JRDesignExpression(Integer.toString(j)));  // -
                        }
                        else {
                            nText.setExpression(new JRDesignExpression(Integer.toString(j)));  // -
                        }

                        nText.setStyle(Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findFirst().get()); // StyleG10
                        nText.setBlankWhenNull(true);
                        nText.setMode(ModeEnum.OPAQUE);
                        nText.getLineBox().getPen().setLineWidth(0.1f);
                        lastJRCrosstabRowGroup.getHeader().getChildren().add(nText);

                        nText = new JRDesignTextField();
                        nText.setHeight(cellHeight_);
                        nText.setX(20);
                        nText.setWidth(300);
                        nText.setExpression(new JRDesignExpression(formatValueExpr(rowGroupNameStr.get(j_))));
                        // ЭТО   вывод строки detail
                        // todo !!! это имя поля по умолчанию обрабатывается - видимо нужен выбор вывода имени
                        // todo !!! 1. надо ли вообще выводить, 2. какой поле - зависит от имени группы

                        nText.setStyle(Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findFirst().get()); // StyleG10
                        nText.setBlankWhenNull(true);
                        nText.setMode(ModeEnum.OPAQUE);
                        nText.getLineBox().getPen().setLineWidth(0.1f);
                        lastJRCrosstabRowGroup.getHeader().getChildren().add(nText);
                    }
                } // проскочили  = заведены первые 3 колонки с основными группировками строк!!!

                ///////////////////////////
                // ЗАВЕДЕМ В ЯЧЕЙКАХ СТАНДАРТНЫЕ СУММЫ МЕР - тут уже контроль вывода итогов!!!!
                // закидываем в ячейки имена, шрифт и фон из стилей!!!
                // формат ячеек - потом из настроек надо будет
                // заголовки ячеек тоже подгружаем
                ////////////////////////

                // временно сработаем со старым списком
                // 1 сделаем свой список

                String[] columnsArr = colColumnEnabledStr.split(",");
                List<Integer> indexColumnData = new ArrayList<>();  //ИНДЕКС КОЛОКНИ ДЛЯ ВЫВОДА

                // количество колонок в каждой ячейке кросстаба будем накапливать тут
                Map<String, Map<Integer, Integer>> colColumsCount = new HashMap<>();
                //
                // ЗАВЕДЕНИЕ МЕР
                //

                for (int s = 0; s < columnsArr.length; s++) {
                    int currPos = (s - 1) / 2;
                    if ((!(columnsArr[s].equals("-") || columnsArr[s].equals("+") || columnsArr[s].equals("=")  || columnsArr[s].equals("!"))) && columnsArr[s - 1].equals("+")) {
                        indexColumnData.add(currPos); //  список индексов по чистому списку мер
                        //поле
                        String classname_ = "";
                        if (colCellType.get(currPos).length() > 1) {
                            addField(columnsArr[s], "", colCellType.get(currPos), fieldsList); // тип забит в поле
                            classname_ = colCellType.get(currPos);
                        } else {
                            addField(columnsArr[s], "", "java.math.BigDecimal", fieldsList); // добавим поле
                            classname_ = "java.math.BigDecimal";
                        }
                        //МЕРА
                        if (colCellMesuareFunctionStr.get(currPos).length() > 1) {
                            addMeasure(columnsArr[s], crosstab, CalculationEnum.SUM, classname_, colCellMesuareFunctionStr.get(currPos));
                        } else {
                            addMeasureSumBigDecimal(columnsArr[s], crosstab, classname_, colCellHeaderNameRatioStr.get(currPos));
                        }
                    } else if ((!(columnsArr[s].equals("-") || columnsArr[s].equals("+") || columnsArr[s].equals("=") || columnsArr[s].equals("!"))) && columnsArr[s - 1].equals("=")) {
                        // ЭТО ПОЛЯ ДЛЯ ФОРМУЛ. Не мера и не группы
                        // заодно заведем поля которые не группы и не меры но в расчетах участвуют - у них пометка равно
                        String classname_ = "";
                        if (colCellType.get(currPos).length() > 1) {
                            addField(columnsArr[s], "", colCellType.get(currPos), fieldsList); // тип забит в поле
                            classname_ = colCellType.get(currPos);
                        } else {
                            addField(columnsArr[s], "", "java.lang.String", fieldsList); // добавим поле
                            classname_ = "java.lang.String";
                        }
                        //мера
                        if (colCellMesuareFunctionStr.get(currPos).length() > 1) {
                            addMeasure(columnsArr[s], crosstab, CalculationEnum.NOTHING, classname_, colCellMesuareFunctionStr.get(currPos));
                        } else {
                            addMeasure(columnsArr[s], crosstab, CalculationEnum.NOTHING, classname_, formatFieldExpr(columnsArr[s])); // это по умолчанию
                        }
                    }
                    else if ((!(columnsArr[s].equals("-") || columnsArr[s].equals("+") || columnsArr[s].equals("=") || columnsArr[s].equals("!"))) && columnsArr[s - 1].equals("!")) {
                        // ЭТО ПОЛЯ ДЛЯ ФОРМУЛ.
                        // заодно заведем поля которые не группы и не меры но в расчетах участвуют - у них пометка равно
                        String classname_ = "";
                        if (colCellType.get(currPos).length() > 1) {
                            addField(columnsArr[s], "", colCellType.get(currPos), fieldsList); // тип забит в поле
                            classname_ = colCellType.get(currPos);
                        } else {
                            addField(columnsArr[s], "", "java.math.BigDecimal", fieldsList); // добавим поле
                            classname_ = "java.math.BigDecimal";
                        }
                        //мера
                        if (colCellMesuareFunctionStr.get(currPos).length() > 1) {
                            addMeasure(columnsArr[s], crosstab, CalculationEnum.SUM, classname_, colCellMesuareFunctionStr.get(currPos));
                        } else {
                            addMeasure(columnsArr[s], crosstab, CalculationEnum.SUM, classname_, formatFieldExpr(columnsArr[s])); // это по умолчанию

                        }
                    }
                }

                // и можно кидать во все ячейки кросстаба
                // 1. если в текущем итоге ГОРИЗОНТАЛЬНОЙ группы выключены итоги и ячейка не помечена как итоговая - то пропускаем
                // ЗАВОДИМ ПОЛЯ:
                for (JRCrosstabCell c : crosstab.getCellsList()) {
                    int currentWidth = 0;
                    currentWidth += cellWidth_;

                    for (int currPos : indexColumnData) {

                        JRDesignCrosstabCell cell = (JRDesignCrosstabCell) c;
                        if (cell.getColumnTotalGroup() == null && compareToBaseSub.get(currPos).equals("+")) {
                            continue;
                        } else if (cell.getColumnTotalGroup() != null) {
                            if (compareToBaseSub.get(currPos).equals("+")) {
                                if (!totalCompareH.get(crosstab.getColumnGroupIndicesMap().get(cell.getColumnTotalGroup())).equals("+")) {
                                    continue;
                                }
                            }
                            else {
                                if (disableTotalByH.get(crosstab.getColumnGroupIndicesMap().get(cell.getColumnTotalGroup())).equals("+")) {
                                    continue;
                                }
                            }
                        }

                        { // запомним какие колонки какие ячейки содержат
                            String nametg_ = "detail";
                            if (cell.getColumnTotalGroup() != null) {
                                nametg_ = cell.getColumnTotalGroup();
                            }

                            if (colColumsCount.get(nametg_) == null) {
                                colColumsCount.put(nametg_, new HashMap<>());
                            }
                            colColumsCount.get(nametg_).put(currPos, cellWidth_); // пока положим ширину - вдруг играться будем
                        }

                        cell.setWidth(currentWidth);
                        JRDesignTextField expr = new JRDesignTextField();
                        expr.setBlankWhenNull(true);
                        expr.setWidth(cellWidth_);
                        expr.setX(currentWidth - cellWidth_);
                        // сдвиг в стиле -1 связан с переносом элементов в шапки след уровня
                        int rgPos_ = crosstab.getRowGroupIndicesMap().getOrDefault(cell.getRowTotalGroup(), 0) - 1;
                        if (cell.getRowTotalGroup() != null && rgPos_ >= 0) {
                            expr.setStyle(
                                    Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG".concat(
                                            rowGroupStyleStr.get(rgPos_)
                                    ))).findFirst().get()
                            );
                        } else { // если нет группы это общий итог
                            expr.setStyle(Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findAny().get());
                        }
                        expr.setMode(ModeEnum.OPAQUE);
                        expr.setHeight(cellHeight_);
                        expr.setTextAdjust(TextAdjustEnum.SCALE_FONT);
                        expr.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
                        expr.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);

                        if (!colCellExpression.get(currPos).equals(" ")) {
                            expr.setExpression(new JRDesignExpression( colCellExpression.get(currPos) ));
                        }
                        else {
                            expr.setExpression(new JRDesignExpression(formatValueExpr(columnsArr[currPos * 2 + 1]).concat(".doubleValue()==0?null:").concat(formatValueExpr(columnsArr[currPos * 2 + 1]))));
                        }

                        if (!colCellFormat.get(currPos).equals(" ")) {
                            expr.setPattern(colCellFormat.get(currPos));
                        }
                        else {
                            expr.setPattern("#,##0.00");
                        }
                        expr.getLineBox().getPen().setLineWidth(0.1f);
                        cell.getContents().getChildren().add(expr);

                        currentWidth += cellWidth_;
                    }
                }

                ///////////////////////////
                //    заведем заголовки в колонках
                ////////////////////////
                JRDesignCrosstabColumnGroup curCol = null;
                if (crosstabWoHeader == 0)
                {
                    for (JRCrosstabColumnGroup col : crosstab.getColumnGroupsList()) {
                        curCol = (JRDesignCrosstabColumnGroup) col;
                        // 10 код + 30 имя + (20) на уровень - 1
                        //curCol.setHeight(10+30+20*(crosstab.getColumnGroupsList().size()-crosstab.getColumnGroupsList().indexOf(col)));
                        curCol.setHeight(20); // размер заголовка
                        //   curCol.getTotalHeader().setWidth(cellWidth_ * colCellHeaderNameStr.size());

                        // TotalHeader
                        JRDesignCellContents colCont = (JRDesignCellContents) col.getTotalHeader();
                        colCont.setHeight(10 + 30 + 20 * (crosstab.getColumnGroupsList().size() - crosstab.getColumnGroupsList().indexOf(col)));
                        colCont.getPropertiesMap().setProperty("com.jaspersoft.studio.layout", "com.jaspersoft.studio.editor.layout.FreeLayout");

                        fillColumnsHeader(curCol.getName(), design, colCellHeaderNameStr, colCellHeaderNameUnitStr, indexColumnData, dockToPreviuosHeader, dockToPreviuosHeaderText, cellWidth_, colCont, colCont.getHeight(), crosstab.getColumnGroupsList().indexOf(col), formatTextExpr("итого"),
                                colColumsCount);

                        // Header
                        colCont = (JRDesignCellContents) col.getHeader();
                        colCont.setHeight(20);

                        JRDesignTextField nf = new JRDesignTextField();
                        nf.setHeight(20);

                        // ширина тут только вычисляемая как ширины текущей и нижних групп
                        int zw = 0;
                        if (colColumsCount.get("detail") != null) {
                            zw += colColumsCount.get("detail").size();
                        }

                        for (int zwx = crosstab.getColumnGroupsList().indexOf(col)+1; zwx < crosstab.getColumnGroupsList().size(); zwx++) {
                            if (colColumsCount.get(crosstab.getColumnGroupsList().get(zwx).getName()) != null) {
                                zw += colColumsCount.get(crosstab.getColumnGroupsList().get(zwx).getName()).size();
                            }
                        }
                        nf.setWidth(cellWidth_ *  zw  ); // todo - если вводить настраиваемые колонки то тут надо считать сумму
                        // попробуем - потом функцию сделаем еще пригодится

                        //nf.setWidth(cellWidth_ * (colColumsCount.get("detail").size()) * (crosstab.getColumnGroupsList().size() - crosstab.getColumnGroupsList().indexOf(col)));
                        //colColumsCount.get( curCol.getName()).size()

                        nf.setExpression(new JRDesignExpression(formatValueExpr(colGroupNameStr.get(crosstab.getColumnGroupsList().indexOf(col)))));
                        nf.setTextAdjust(TextAdjustEnum.SCALE_FONT);
                        nf.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
                        nf.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
                        nf.setMode(ModeEnum.OPAQUE);
                        nf.setBlankWhenNull(true);
                        nf.setStyle(Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findAny().get());
                        nf.setFontSize(12f);
                        nf.getLineBox().getPen().setLineWidth(0.1f);
                        //((JRDesignCrosstabColumnGroup) col).setPosition(CrosstabColumnPositionEnum.STRETCH);
                        colCont.getChildren().add(nf);
                    }

                    // в самом нижнем заголовке повторяются поля
                    curCol.setHeight(10 + 30 + 20);
                    fillColumnsHeader("detail", design, colCellHeaderNameStr,colCellHeaderNameUnitStr, indexColumnData, dockToPreviuosHeader, dockToPreviuosHeaderText, cellWidth_, (JRDesignCellContents) curCol.getHeader(), 10 + 30 + 20, crosstab.getColumnGroupsList().indexOf(curCol) + 1, null,
                            colColumsCount       );
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JRXmlWriter.writeReport(design, out ,"UTF-8"); //ISO-8859-1   UTF-8
            RawFileData rf = new RawFileData(out);
            findProperty("bRepRawFile").change(rf, context);
        } catch (JRException | ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
