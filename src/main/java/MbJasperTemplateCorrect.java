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
import net.sf.jasperreports.crosstabs.type.CrosstabTotalPositionEnum;
import net.sf.jasperreports.engine.JRChild;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRStyle;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.type.*;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JRXmlWriter;


import java.io.*;
import java.sql.SQLException;
import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;


public class MbJasperTemplateCorrect  extends InternalAction {

    public MbJasperTemplateCorrect(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    void fillNameInField(String nameField, Integer maxLevel, List<String> in, List<JRField> fieldsList) {
         for (int i = 0; i <= maxLevel + 1 && i < in.size(); i++) {
            int ii = i + 1;//maxLevel - i;
            JRDesignField f = (JRDesignField)fieldsList.stream().filter(s -> s.getName().equals(nameField.concat(Integer.toString(ii))) ).findAny().get();
            if (f != null) {
                f.setName(in.get(i));
            }
        }
    }

    void addFields(List<String> in, List<JRField> fieldsList, String prefix) {
        for (String name: in) {
            JRDesignField f = new JRDesignField();
            f.setName(prefix.concat(name));
            f.setValueClassName("java.lang.String");// f.setValueClass(String.class);
            fieldsList.add(f);
        }
    }

    void addFieldBigDecimal( String name, List<JRField> fieldsList) {
            JRDesignField f = new JRDesignField();
            f.setName( name );
            f.setValueClassName("java.math.BigDecimal");
            fieldsList.add(f);
    }

    String formatFieldExpr(String name) {
        return "$F{".concat(name).concat("}");
    }
    String formatValueExpr(String name) {
        return "$V{".concat(name).concat("}");
    }

    void addRowGroup(List<String> in, JRDesignCrosstab crossTab) {
        int vWidth = 320; // ширина 1 колонки - потом можно будет   и дополнять
        int vHeight = 13;
        // одна группа всегда существует
        JRDesignCrosstabRowGroup prevJRCrosstabRowGroup = null;
        JRDesignCrosstabRowGroup firstJRCrosstabRowGroup = null;
        JRDesignCrosstabRowGroup rg = (JRDesignCrosstabRowGroup) Arrays.stream(crossTab.getRowGroups()).toList().getFirst();
        for (int i = 0; i < in.size(); i++) {
            if (i==0 && rg != null) {
                    // преднастройки 1 группы
                rg.setTotalPosition(CrosstabTotalPositionEnum.END); // !!! Первая в конец

                //-----------------------

                if (rg.getBucket().getExpression()!=null) {
                    JRDesignExpressionChunk ch = (JRDesignExpressionChunk) Arrays.stream(rg.getBucket().getExpression().getChunks()).toList().getFirst();
                    ch.setText(in.get(i));
                }
                else {
                    ((JRDesignCrosstabBucket) rg.getBucket()).setExpression(new JRDesignExpression(formatFieldExpr(in.get(i))));
                    ((JRDesignCrosstabBucket) rg.getBucket()).setValueClassName("java.lang.String");
                }
                prevJRCrosstabRowGroup = rg;
                firstJRCrosstabRowGroup = rg;
            }
            else {
                JRDesignCrosstabRowGroup nGroup = new JRDesignCrosstabRowGroup();
                crossTab.getRowGroupsList().add(nGroup);


                // преднастройки остальные группы
                nGroup.setTotalPosition(CrosstabTotalPositionEnum.START); //!!! для последующих начало // prevJRCrosstabRowGroup.getTotalPositionValue());

                //-------------------------------
                nGroup.setPosition(prevJRCrosstabRowGroup.getPositionValue());
                nGroup.setName(firstJRCrosstabRowGroup.getName().concat(Integer.toString(i)));
                crossTab.getRowGroupIndicesMap().put(nGroup.getName(), i);
                JRDesignCrosstabBucket nBucket = new JRDesignCrosstabBucket();
                nBucket.setValueClassName("java.lang.String");
                nGroup.setBucket(nBucket);
                // переменная группировки
                JRDesignExpressionChunk nChunk = new JRDesignExpressionChunk();
                nBucket.setExpression(new JRDesignExpression(formatFieldExpr(in.get(i))));
                prevJRCrosstabRowGroup = nGroup;
            }

            // преднастройки для всех
            prevJRCrosstabRowGroup.setWidth(0);
            ((JRDesignCellContents) prevJRCrosstabRowGroup.getTotalHeader()).setHeight(vHeight);
            //((JRDesignCellContents) rg.getTotalHeader()).
            //----------------------
        }
        // преднастройки ПОСЛЕДНЯЯ группа
        prevJRCrosstabRowGroup.setWidth(vWidth);
        ((JRDesignCellContents) prevJRCrosstabRowGroup.getHeader()).setHeight(vHeight);

        // ------------------------------
    }

    void addColumnGroup(List<String> in, JRDesignCrosstab crossTab) {
        int vHeight = 13;

        // одна группа всегда существует
        JRDesignCrosstabColumnGroup prevJRCrosstabColGroup = null;
        JRDesignCrosstabColumnGroup firstJRCrosstabColGroup = null;
        JRDesignCrosstabColumnGroup rg = (JRDesignCrosstabColumnGroup)Arrays.stream(crossTab.getColumnGroups()).toList().getFirst();
        for (int i = 0; i < in.size(); i++) {
            if (i==0 && rg != null) {

                    // rg.setTotalPosition(CrosstabTotalPositionEnum.END); // !!! Первая в конец

                    if (rg.getBucket().getExpression()!=null) {
                        JRDesignExpressionChunk ch = (JRDesignExpressionChunk) Arrays.stream(rg.getBucket().getExpression().getChunks()).toList().getFirst();
                        ch.setText(formatFieldExpr(in.get(i)));
                    }
                    else {
                        ((JRDesignCrosstabBucket) rg.getBucket()).setExpression(new JRDesignExpression(formatFieldExpr(in.get(i))));
                    }
                    prevJRCrosstabColGroup = rg;
                    firstJRCrosstabColGroup = rg;
            }
            else {
                JRDesignCrosstabColumnGroup nGroup = new JRDesignCrosstabColumnGroup();
                crossTab.getColumnGroupsList().add(nGroup);
                nGroup.setHeight(prevJRCrosstabColGroup.getHeight());
                nGroup.setPosition(prevJRCrosstabColGroup.getPositionValue());
                nGroup.setTotalPosition(CrosstabTotalPositionEnum.START);   //prevJRCrosstabColGroup.getTotalPositionValue());
                nGroup.setName(firstJRCrosstabColGroup.getName().concat(Integer.toString(i)));
                crossTab.getColumnGroupIndicesMap().put(nGroup.getName(), i);
                JRDesignCrosstabBucket nBucket = new JRDesignCrosstabBucket();
                nGroup.setBucket(nBucket);
                nBucket.setValueClassName(prevJRCrosstabColGroup.getBucket().getValueClassName());
                // переменная группировки
                JRDesignExpressionChunk nChunk = new JRDesignExpressionChunk();
                nBucket.setExpression(new JRDesignExpression(formatFieldExpr(in.get(i))));
                prevJRCrosstabColGroup = nGroup;
            }
            // Преднастройка для всех
            prevJRCrosstabColGroup.setHeight(vHeight);
        }
    }
//    JRCrosstabColumnGroup prevJRCrosstabColGroup = null;
//    JRCrosstabColumnGroup firstJRCrosstabColGroup = null;
//        for (int i = 0; i < in.size(); i++) {
//        if (i==0) {
//

    void addMeasures(List<String> in, JRDesignCrosstab crossTab) {
        for (String name: in) {
            JRDesignCrosstabMeasure f = new JRDesignCrosstabMeasure();
            f.setCalculation(CalculationEnum.NOTHING);
            f.setValueClassName("java.lang.String");
            f.setName(name);
            f.setValueExpression(new JRDesignExpression(formatFieldExpr(name)));
            crossTab.getMesuresList().add(f);
            crossTab.getMeasureIndicesMap().put(name,crossTab.getMeasureIndicesMap().size()); // скорей всего надо - но что c индексом?
        }
    }

    void addMeasureSumBigDecimal(String name, JRDesignCrosstab crossTab) {
            JRDesignCrosstabMeasure f = new JRDesignCrosstabMeasure();
            f.setCalculation(CalculationEnum.SUM);
            f.setValueClassName("java.math.BigDecimal");
            f.setName(name);
            f.setValueExpression(new JRDesignExpression(formatFieldExpr(name)));
            crossTab.getMesuresList().add(f);
            crossTab.getMeasureIndicesMap().put(name,crossTab.getMeasureIndicesMap().size()); // скорей всего надо - но что c индексом?
    }

    void fillNameRowGroup(String nameField, Integer maxLevel, List<String> in, JRDesignCrosstab crossTab) {
        for (int i = 0; i <= maxLevel + 1 && i < in.size(); i++) {
            int ii = i + 1;//maxLevel - i;
            for (JRCrosstabRowGroup rg : crossTab.getRowGroups()) {
                if (rg != null &&  rg.getName().equals(nameField.concat(Integer.toString(ii))) ) {
                    JRDesignExpressionChunk ch = (JRDesignExpressionChunk)Arrays.stream(rg.getBucket().getExpression().getChunks()).findAny().get();
                    ch.setText(in.get(i));
                }
            }
        }
    }

    void fillNameColumnGroup(String nameField, Integer maxLevel, List<String> in, JRDesignCrosstab crossTab) {
        for (int i = 0; i <= maxLevel + 1 && i < in.size(); i++) {
            int ii = i + 1;//maxLevel - i;
            for (JRCrosstabColumnGroup rg : crossTab.getColumnGroups()) {
                if (rg != null &&  rg.getName().equals(nameField.concat(Integer.toString(ii))) ) {
                    JRDesignExpressionChunk ch = (JRDesignExpressionChunk)Arrays.stream(rg.getBucket().getExpression().getChunks()).findAny().get();
                    ch.setText(in.get(i));
                }
            }
        }
    }

    void fillNameMeasures(String nameField, Integer maxLevel, List<String> in, JRDesignCrosstab crossTab) {
        for (int i = 0; i <= maxLevel + 1 && i < in.size(); i++) {
            int ii = i + 1;//maxLevel - i;
            for (JRCrosstabMeasure rg : crossTab.getMeasures()) {
                if (rg != null &&  rg.getName().equals(nameField.concat(Integer.toString(ii))) ) {
                    JRDesignExpressionChunk ch = (JRDesignExpressionChunk)Arrays.stream((rg).getValueExpression().getChunks()).findAny().get();
                    ch.setText(in.get(i));
                }
            }
        }
    }

    void hideRow(String nameField, Integer maxLevel, Integer startLevel, JRDesignCrosstab crossTab) {
        // делаем высоту 0 для второго неизмененного и далее элемента
        for (int i = startLevel+2; i <= maxLevel + 1; i++) {

            for (JRCrosstabRowGroup rg : crossTab.getRowGroups()) {
                if (rg != null &&  rg.getName().equals(nameField.concat(Integer.toString(i))) ) {
                    ((JRDesignCellContents)rg.getHeader()).setHeight(0);
                    for (JRChild ch : ((JRDesignCrosstabRowGroup)rg).getHeader().getChildren()){
                        if (JRDesignTextField.class.isInstance (ch)) {
                            ((JRDesignTextField)ch).setHeight(0);
                        }
                    }
                    for (JRChild ch : ((JRDesignCrosstabRowGroup)rg).getTotalHeader().getChildren()){
                        ((JRDesignCellContents)rg.getHeader()).setHeight(0);
                        if (JRDesignTextField.class.isInstance (ch)) {
                            ((JRDesignTextField)ch).setHeight(0);
                        }
                    }
                }
            }
            for (JRCrosstabCell cell: crossTab.getCellsList()) {
                if (cell.getRowTotalGroup() != null && cell.getRowTotalGroup().equals(nameField.concat(Integer.toString(i))) ) {
                    ((JRDesignCrosstabCell)cell).setHeight(0);
                    if (cell.getContents() != null) {
                        ((JRDesignCellContents)((JRDesignCrosstabCell)cell).getContents()).setHeight(0);
                        for (JRChild ch : ((JRDesignCrosstabCell)cell).getContents().getChildren()){
                            if (JRDesignTextField.class.isInstance (ch)) {
                                ((JRDesignTextField)ch).setHeight(0);
                            }
                        }
                    }
                }
            }
        }
    }

    void hideTotalColumnGroup(String nameField, Integer maxLevel, Integer startLevel, JRDesignCrosstab crossTab) {
        // делаем ширину 0 для второго неизмененного и далее элемента
        for (int i = startLevel+1; i <= maxLevel; i++) {

            for (JRCrosstabColumnGroup rg : crossTab.getColumnGroups()) {
                if (rg != null &&  rg.getName().equals(nameField.concat(Integer.toString(i))) ) {
                    ((JRDesignCrosstabColumnGroup)rg).setTotalPosition(CrosstabTotalPositionEnum.NONE);


                    for (JRChild ch : ((JRDesignCrosstabColumnGroup)rg).getHeader().getChildren()){
                        if (JRDesignTextField.class.isInstance (ch)) {
                            ((JRDesignTextField)ch).setWidth(0);
                        }
                    }
                    for (JRChild ch : ((JRDesignCrosstabColumnGroup)rg).getTotalHeader().getChildren()){
                        //((JRDesignCellContents)rg.getTotalHeader()).set);
                        if (JRDesignTextField.class.isInstance (ch)) {
                            ((JRDesignTextField)ch).setWidth(0);
                        }
                    }
                }
            }
        }
    }

    void setStyleRow(String groupCode, List<String> styleNames, JRStyle[] style, JRDesignCrosstab crossTab) {

        for (int i = 0; i < styleNames.size()-1; i++) { // нижний уровень не меняем стиль
            int ii = i;
            JRStyle st = Arrays.stream(style).filter(s -> s.getName().equals( "StyleG".concat(styleNames.get(ii)))).findAny().get();
            if (st != null) {
                for (JRCrosstabRowGroup rg : crossTab.getRowGroups()) {
                    if (rg != null &&  rg.getName().equals(groupCode.concat(Integer.toString(i+2))) ) {
                        for (JRChild ch : ((JRDesignCrosstabRowGroup)rg).getTotalHeader().getChildren()){
                            if (JRDesignTextField.class.isInstance (ch)) {
                                ((JRDesignTextField)ch).setStyle(st);
                            }
                        }
                    }

                    for (JRCrosstabCell cell: crossTab.getCellsList()) {
                        if (cell.getRowTotalGroup() != null && cell.getRowTotalGroup().equals( groupCode.concat(Integer.toString(i+2) ) ) ) { // ячейки уровнем ниже

                            if (cell.getContents() != null) {

                                for (JRChild ch : ((JRDesignCrosstabCell)cell).getContents().getChildren()){
                                    if (JRDesignTextField.class.isInstance (ch)) {
                                        ((JRDesignTextField)ch).setStyle(st);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }




    Integer sortFieldsCell(  ArrayList<Integer> posY,   ArrayList<JRDesignTextField>  toPrint ) {
        posY.sort((o1, o2) -> Integer.compare(o1,o2));
        toPrint.stream().sorted((o1, o2) -> Integer.compare(o1.getX(), o2.getX()));
        int totalMaxW = 0;
        for ( Integer j : posY) { // по каждой координате у отдельно
            Integer x = null;
            int xCurrent = 0;
            int xCurrentW = 0;
            Integer totalW = 0;

            for (JRDesignTextField fld : toPrint) {

                if (fld.getY() == j) {
                    if (x == null) {
                        x = fld.getX();
                        fld.setX(xCurrent);
                        xCurrentW = fld.getWidth();
                    }
                    else if (x == fld.getX()) {
                        fld.setX(xCurrent);
                        xCurrentW = max(xCurrentW, fld.getWidth());
                    }
                    else {
                        xCurrent  = xCurrent + xCurrentW;
                        xCurrentW = fld.getWidth();
                        x = fld.getX();
                    }
                }
            }
            totalMaxW = max(totalMaxW, xCurrent + xCurrentW);
        }
        return totalMaxW;
    }
    // Чтобы манипулировать колонками - формулы могут быть любыми - все должны быть помечены повторяющимися именами <textField  <property name="com.jaspersoft.studio.element.name" value="value"/>
    // в отчете индивидуальное количество показателей. итоговая группа показателей редактируется и в отчете и в шаблоне индивидуально
    // 1 надо знать какой элемент имеет общую ширину
    // 2 какие элементы менять по ширине и позиции- тут можно их п
    // пойдем по схеме - групп всего 3
    // колонки для выключения ищем в контейнерах:
    // columnGroup(hGroupCode1) - crosstabColumnHeader & crosstabTotalColumnHeader -> cellContents
    //                                  1w                       1w
    // columnGroup(hGroupCode2) - crosstabColumnHeader & crosstabTotalColumnHeader -> cellContents
    //                                 2w                       1w
    // columnGroup(hGroupCode2) - crosstabColumnHeader & crosstabTotalColumnHeader -> cellContents
    //                                  3w                       1w
    //  по ячейкам
    //
    // 1. ширина входящих в отчет колонок любая. общую ширину берем по заголовку по входящим полям. УБИРАЕМ исключаемые поля
    // 2. в каждой группе смотрим по

    // может просто попереклеить колонки по указанному порядку ???? - колонки переклеиваются !!
    // как их добавлять?
    //0. заголовок колонки   1. нужно имя переменной, 2. имя меры 3. формула меры 4. ширина колонки - в принципе не сужаем а расширяем - что тоже неплохо и не надо опираться на имена
    // хватит 1 базовой колонки

    // К УДАЛЕНИЮ!!!
    void disableColums( String nameField, String columns,  Integer maxLevel, JRDesignCrosstab crossTab) {
        Integer totalW = 0;
        int[] maxWidthGroup = new int[4];
        ArrayList<Integer>            posY    = new ArrayList<>();
        ArrayList<JRDesignTextField>  toPrint = new ArrayList<>();

        for (int i = 0; i < maxLevel; i++) {
            for (JRCrosstabColumnGroup rg : crossTab.getColumnGroups()) {
                if (rg != null && rg.getName().equals(nameField.concat(Integer.toString(i+1))) ) {
                    posY.clear();
                    toPrint.clear();
                    for (JRChild ch : ((JRDesignCrosstabColumnGroup)rg).getHeader().getChildren()){
                        if (JRDesignTextField.class.isInstance (ch) ) {
                            if  ( ((JRDesignTextField)ch).getPropertiesMap().containsProperty("com.jaspersoft.studio.element.name")
                                   &&
                                   columns.contains("-,".concat((((JRDesignTextField)ch).getPropertiesMap().getProperty("com.jaspersoft.studio.element.name") )))
                            ) {
                              ((JRDesignTextField)ch).setX(0);
                              ((JRDesignTextField)ch).setWidth(0);
                            }
                            else {
                                if (!posY.contains(((JRDesignTextField) ch).getY())) {
                                    posY.add(((JRDesignTextField) ch).getY());
                                }
                              toPrint.add((JRDesignTextField)ch);
                            }
                            // header выше нижнего - являются шапками - шапки и содержимое не должны быть шире суммарной ширины нижних уровней
                            if (i > 0 && totalW  <  ((JRDesignTextField)ch).getWidth()+((JRDesignTextField)ch).getX()  ) {
                                ((JRDesignTextField)ch).setWidth(totalW - ((JRDesignTextField)ch).getX());
                            }
                        }
                    }
                    // только самый нижний header увеличивает ширину
                    if (i == 0) {
                        maxWidthGroup[i] = sortFieldsCell(posY, toPrint);
                        totalW = totalW +   maxWidthGroup[i] ;
                    }
                    else {
                        totalW = totalW + sortFieldsCell(posY, toPrint);
                    }


                 //  ((JRDesignCrosstabColumnGroup)rg).getTotalHeader().getChildren().sort((o1, o2) -> Integer.compare(((JRDesignTextField)o1).getX(),((JRDesignTextField)o2).getX()) );
                    posY.clear();
                    toPrint.clear();

                    for (JRChild ch : ((JRDesignCrosstabColumnGroup)rg).getTotalHeader().getChildren()){
                        if (JRDesignTextField.class.isInstance (ch) ) {
                            if  (
                                    ((JRDesignTextField)ch).getPropertiesMap().containsProperty("com.jaspersoft.studio.element.name")
                                            &&
                                            columns.contains("-,".concat((((JRDesignTextField)ch).getPropertiesMap().getProperty("com.jaspersoft.studio.element.name") )))
                            ) {
                                ((JRDesignTextField)ch).setX(0);
                                ((JRDesignTextField)ch).setWidth(0);
                            }
                            else {
                                 if (!posY.contains(((JRDesignTextField) ch).getY())) {
                                      posY.add(((JRDesignTextField) ch).getY());
                                 }
                                 toPrint.add((JRDesignTextField)ch);
                           }
                        }
                   }
                        maxWidthGroup[i+1] = sortFieldsCell(posY, toPrint);
                        totalW = totalW + maxWidthGroup[i+1];

                }
            }
        }


        for (JRCrosstabCell cell : crossTab.getCellsList()) {
          //  cell.getContents().getChildren().sort( (o1, o2) -> Integer.compare(((JRDesignTextField)o1).getX(),((JRDesignTextField)o2).getX()) );
            posY.clear();
            toPrint.clear();
            for (JRChild ch : cell.getContents().getChildren()){
                if (JRDesignTextField.class.isInstance (ch) ) {
                    if  (
                            ((JRDesignTextField)ch).getPropertiesMap().containsProperty("com.jaspersoft.studio.element.name")
                                    &&
                                    columns.contains("-,".concat((((JRDesignTextField)ch).getPropertiesMap().getProperty("com.jaspersoft.studio.element.name") )))
                    ) {
                        ((JRDesignTextField)ch).setX(0);
                        ((JRDesignTextField)ch).setWidth(0);
                    }
                    else {
                        if (!posY.contains(((JRDesignTextField) ch).getY())) {
                            posY.add(((JRDesignTextField) ch).getY());
                        }
                        toPrint.add((JRDesignTextField)ch);
                   }
                }
            }
            int currWidth = sortFieldsCell(posY, toPrint);

            if (maxWidthGroup[0]  > 0
                    && (cell.getRowTotalGroup() == null || cell.getRowTotalGroup().isEmpty())
                    && (cell.getColumnTotalGroup() == null || cell.getColumnTotalGroup().isEmpty()) ) {
                // в это ячейке лежит ширина колонки header самой нижней в иерархии
               ((JRDesignCrosstabCell)cell).setWidth(maxWidthGroup[0]);
            }
            else if( !(cell.getColumnTotalGroup() == null || cell.getColumnTotalGroup().isEmpty())  && (cell.getRowTotalGroup() == null || cell.getRowTotalGroup().isEmpty())) {
                // в этих ячейках общая ширина колонок сводной лежит
                for (int i = 0; i < maxLevel; i++) {
                        if (cell.getColumnTotalGroup() .equals(nameField.concat(Integer.toString(i+1))) ) {
                            ((JRDesignCrosstabCell)cell).setWidth( maxWidthGroup[i+1]);
                        }
                }
            }
            else
                if (currWidth > 0 && cell.getWidth() != currWidth) {
                ((JRDesignCrosstabCell)cell).setWidth(currWidth);
            }
        }
    }



    /// /////////////////////////////////////////
    ///
    ///
    // вариант 2
    /// /////////////////////////////////////////////
    //1. перебираем все колонки, по первому проходу вычисляем уменьшение ширины - дельту
    //2. расставляем по координатам согласно списка
    void disableColumsV2( String nameField, String columns,  Integer maxLevel, JRDesignCrosstab crossTab) {
        String[] columnsArr = columns.split(",");
        // long countRemove = Arrays.stream(columnsArr).filter(a -> a.equals("-")).count(); // сколько полей убираем
        // есть header,  totalHeader и crossTab.getCellsList()
        // 1. header,totalHeader - должна убавляться общая ширина - пока не знаю как - из общей вычитать??? но если групп много???
        // 2. удалять ячейки не положенные к выдаче
        // 3. ставить ячейки в порядке их появления в nameFields[]

        // начнем с 3 задачи crossTab.getCellsList()
        int posX = 0; // позиция х элементов
        int deletedW = 0; // ширина удаляемых элементов

        // все текстовые поля
        ArrayList<Object> textFieldsCell = new ArrayList<>();
        Map<Object,Integer> maxWcell = new HashMap<>();

       // ArrayList<JRDesignCellContents> textFieldsCellcont = new ArrayList<>();
       // Map<JRDesignCellContents,Integer> maxWcellcont = new HashMap<>();

        ArrayList<JRDesignTextField> textFields = new ArrayList<>();
        ArrayList<ArrayList<JRDesignTextField>> parentFields = new ArrayList<>(); // ссылки на список для удаления
        // ВСЕ ЯЧЕЙКИ КРОСТАБА - текстовые поля внутри ячеек,  (тп и являются шаблоном)
        for (JRCrosstabCell cell : crossTab.getCellsList()) {
            for (JRChild ch : cell.getContents().getChildren()) { //<crosstabCell <cellContents>
                if (ch instanceof JRDesignTextField) { // если это <textField
                    textFields.add((JRDesignTextField)ch); //тектстовое поле
                    parentFields.add((ArrayList)cell.getContents().getChildren()); // список в котором сами текстовые поля все
                    textFieldsCell.add((JRDesignCrosstabCell)cell); //сюда ячейку кростаба
                }
            }
        }
        for (JRCrosstabColumnGroup rg : crossTab.getColumnGroups()) {
            for (JRChild ch : ((JRDesignCrosstabColumnGroup)rg).getHeader().getChildren()) {
                if (ch instanceof JRDesignTextField) {
                    textFields.add((JRDesignTextField)ch);
                    parentFields.add((ArrayList)((JRDesignCrosstabColumnGroup)rg).getHeader().getChildren());
                    textFieldsCell.add((JRDesignCellContents)((JRDesignCrosstabColumnGroup)rg).getHeader());
                }
            }
            for (JRChild ch : ((JRDesignCrosstabColumnGroup)rg).getTotalHeader().getChildren()) {
                if (ch instanceof JRDesignTextField) {
                    textFields.add((JRDesignTextField)ch);
                    parentFields.add((ArrayList)((JRDesignCrosstabColumnGroup)rg).getHeader().getChildren());
                    textFieldsCell.add((JRDesignCellContents)((JRDesignCrosstabColumnGroup)rg).getHeader());
                }
            }
        }

        for (int s = 0; s < columnsArr.length; s++) {
            if (!(columnsArr[s].equals("-")||columnsArr[s].equals("+"))) {
                int ss = s;
                int posX_  = posX;
                int currentW = 0;
                // ВЫБОР ВСЕХ ПОЛЕЙ С ИДЕНТИФИКАТОРОМ РАВНЫМ НАЗВАНИЮ МЕРЫ - это вариант когда ячейки удаляются по отметке идентификатором
                List<JRDesignTextField> flds =  textFields.stream().filter(a ->
                                a.getPropertiesMap().containsProperty("com.jaspersoft.studio.element.name")
                                &&
                                columnsArr[ss].equals(a.getPropertiesMap().getProperty("com.jaspersoft.studio.element.name"))
                ).toList();

                int maxX = 0;
                int minX = 0;
                if (!flds.isEmpty()) {
                    maxX = flds.getFirst().getX() +  flds.getFirst().getWidth();
                    minX = flds.getFirst().getX() ;
                    for (JRDesignTextField f : flds) {
                          maxX = max( f.getX() +  f.getWidth(), maxX);
                          minX = min( f.getX(), minX);
                    }
                    currentW = maxX - minX;
                }

                if (columnsArr[s-1].equals("-")) {
                    deletedW +=  currentW;
                    for (int j = 0; j < textFields.size(); j++) { // тут нужен индекс оригинальный
                        if (textFields.get(j).getPropertiesMap().containsProperty("com.jaspersoft.studio.element.name")
                                &&
                                columnsArr[ss].equals(textFields.get(j).getPropertiesMap().getProperty("com.jaspersoft.studio.element.name"))
                        )  {
                            maxWcell.put(textFieldsCell.get(j), posX);
                            parentFields.get(j).remove(textFields.get(j));
                        }
                    }
                }
                else {
                    for (int j = 0; j < textFields.size(); j++) {
                        if (textFields.get(j).getPropertiesMap().containsProperty("com.jaspersoft.studio.element.name")
                                &&
                                columnsArr[ss].equals(textFields.get(j).getPropertiesMap().getProperty("com.jaspersoft.studio.element.name"))
                        )  {
                            maxWcell.put(textFieldsCell.get(j), posX + currentW);
                            textFields.get(j).setX(posX + textFields.get(j).getX() - minX);
                        }
                    }
                    posX += currentW;
                }
            }
        }
        JRCrosstabColumnGroup rg;
        int maxGroupW = 0;
        int maxGroupW0 = 0;
        int maxGroupWcurr = 0;
        // по иерархии вверх надо - с нижнего уровня иерархии - все ячейки дб не шире того что запомнено.
        for (int xg = crossTab.getColumnGroups().length-1; xg >= 0; xg--) { //for (JRCrosstabColumnGroup rg : crossTab.getColumnGroups()) {
            rg = crossTab.getColumnGroups()[xg];
            maxGroupWcurr = 0;
            for (JRChild ch : ((JRDesignCrosstabColumnGroup)rg).getHeader().getChildren()) {
                if ((JRDesignTextField.class.isInstance(ch)) && maxWcell.get( ((JRDesignCrosstabColumnGroup)rg).getHeader() )  != null
                     && (((JRDesignTextField)ch).getWidth() + ((JRDesignTextField)ch).getX()) > maxWcell.get( ((JRDesignCrosstabColumnGroup)rg).getHeader() )
                )
                {
                    ((JRDesignTextField)ch).setWidth( maxWcell.get( ((JRDesignCrosstabColumnGroup)rg).getHeader() ) - ((JRDesignTextField)ch).getX());
                }
                else if  (maxWcell.get( ((JRDesignCrosstabColumnGroup)rg).getHeader() ) == null && maxGroupW < ((JRDesignTextField)ch).getWidth()) {
                    ((JRDesignTextField)ch).setWidth(maxGroupW);  // если в этоя ячейке были регулируемые поля то сюда не зайдет - а если не было то снизу приходит
                }
                maxGroupWcurr = max(maxGroupWcurr,((JRDesignTextField)ch).getWidth() + ((JRDesignTextField)ch).getX());
            }


            if (xg == crossTab.getColumnGroups().length-1) {
                maxGroupW  = maxGroupWcurr;
                maxGroupW0 = maxGroupWcurr;
            }

            maxGroupWcurr = 0;
            for (JRChild ch : ((JRDesignCrosstabColumnGroup)rg).getTotalHeader().getChildren()) {
                if ((JRDesignTextField.class.isInstance(ch)) && maxWcell.get( ((JRDesignCrosstabColumnGroup)rg).getHeader() )  != null
                        && (((JRDesignTextField)ch).getWidth() + ((JRDesignTextField)ch).getX()) > maxWcell.get( ((JRDesignCrosstabColumnGroup)rg).getHeader() )
                )
                {
                    ((JRDesignTextField)ch).setWidth( maxWcell.get( ((JRDesignCrosstabColumnGroup)rg).getHeader() ) - ((JRDesignTextField)ch).getX());
                }
                maxGroupWcurr = max(maxGroupWcurr,((JRDesignTextField)ch).getWidth() + ((JRDesignTextField)ch).getX());
              //  else if (maxGroupWcurr < ((JRDesignTextField)ch).getWidth()) {
              //    ((JRDesignTextField)ch).setWidth(maxGroupWcurr);  // если в этоя ячейке были регулируемые поля то сюда не зайдет - а если не было то снизу приходит
              //}
            }
            maxGroupW += maxGroupWcurr; // выше нижней только присоединяем
        }


        //   по всем ячейкам
        for (JRCrosstabCell cell : crossTab.getCellsList()) {
            if (maxWcell.get(cell) != null) {
                ((JRDesignCrosstabCell) cell).setWidth(maxWcell.get(cell));
            }
            if (  (cell.getRowTotalGroup()    == null || cell.getRowTotalGroup().isEmpty())
               && (cell.getColumnTotalGroup() == null || cell.getColumnTotalGroup().isEmpty()) ) {
                // в это ячейке лежит ширина колонки header самой нижней в иерархии
                ((JRDesignCrosstabCell)cell).setWidth(maxGroupW0);
            }
        }


    }
    // колонка ячеек шаблона может быть создана в любой итоговой группе. шаблонные ячейки нужны ли так? - может просто создавать?
    void insertColumn( String nameField, String columns,  Integer maxLevel, JRDesignCrosstab crossTab) {
        String[] columnsArr = columns.split(","); // нечетные флаг, четный имя колонки



    }


    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        FileData fileIn = (FileData) getParam(0, context);
        int modeTemplate = (int) getParam(1, context);
        List<String> rowGroupCodeStr = Arrays.asList(((String) getParam(2, context)).split("\\s*,\\s*"));
        List<String> rowGroupNameStr = Arrays.asList(((String) getParam(3, context)).split("\\s*,\\s*"));
        List<String> rowGroupStyleStr = Arrays.asList(((String) getParam(4, context)).split("\\s*,\\s*"));
        List<String> colGroupCodeStr = Arrays.asList(((String) getParam(5, context)).split("\\s*,\\s*"));
        List<String> colGroupNameStr = Arrays.asList(((String) getParam(6, context)).split("\\s*,\\s*"));
        String colColumnEnabledStr = (String) getParam(7, context);



        try {
            JasperDesign design = JRXmlLoader.load(fileIn.getRawFile().getInputStream());
            List<JRField> fieldsList = design.getMainDesignDataset().getFieldsList();

            if (modeTemplate == 0) {
                // 1 переименуем поля
                fillNameInField("vGroupCode", 16, rowGroupCodeStr, fieldsList);
                fillNameInField("vGroupName", 16, rowGroupNameStr, fieldsList);


                fillNameInField("hGroupCode", 3, colGroupCodeStr, fieldsList);
                fillNameInField("hGroupName", 3, colGroupNameStr, fieldsList);


                // 2 корректируем выражения
                JRDesignCrosstab crosstab = (JRDesignCrosstab) design.getSummary().getChildren().stream().filter(ct -> JRDesignCrosstab.class.isInstance(ct)).findAny().get(); // единственный - но возможно по имени на поиск заменить тут
                if (crosstab != null) {
                    // сопоставление полей
                    fillNameRowGroup("vGroupCode", 16, rowGroupCodeStr, crosstab);
                    fillNameColumnGroup("hGroupCode", 3, colGroupCodeStr, crosstab);

                    fillNameMeasures("vGroupCode", 16, rowGroupCodeStr, crosstab);
                    fillNameMeasures("vGroupName", 16, rowGroupNameStr, crosstab);
                    fillNameMeasures("hGroupCode", 3, colGroupCodeStr, crosstab);
                    fillNameMeasures("hGroupName", 3, colGroupNameStr, crosstab);

                    // удаление пустых групп
                    hideRow("vGroupCode", 16, rowGroupCodeStr.size(), crosstab);
                    // удаление пустых итогов по вертикали
                    hideTotalColumnGroup("hGroupCode", 3, colGroupCodeStr.size(), crosstab);
                    setStyleRow("vGroupCode", rowGroupStyleStr, design.getStyles(), crosstab);
                    disableColumsV2("hGroupCode", colColumnEnabledStr, 3, crosstab);
                }
            }
            else {
                //1 добавим поля колонок и строк
                addFields(rowGroupCodeStr, fieldsList, "");
                addFields(rowGroupNameStr, fieldsList, "");
                addFields(rowGroupStyleStr, fieldsList, "style");
                addFields(colGroupCodeStr, fieldsList, "");
                addFields(colGroupNameStr, fieldsList, "");
                // в Summary Единственный и первый попавшийся кростаб
                JRDesignCrosstab crosstab = (JRDesignCrosstab) design.getSummary().getChildren().stream().filter(JRDesignCrosstab.class::isInstance).findAny().get();
                if (crosstab != null) {

                    addRowGroup(rowGroupCodeStr, crosstab);
                    addColumnGroup(colGroupCodeStr, crosstab);

                    addMeasures(rowGroupCodeStr, crosstab);
                    addMeasures(rowGroupNameStr, crosstab);
                    addMeasures(colGroupCodeStr, crosstab);
                    addMeasures(colGroupNameStr, crosstab);

                 ///////////////////////////
                 // РАЗДЕЛ ЗАВЕДЕНИЯ ЯЧЕЕК
                 ////////////////////////

                    int cellHeight_ = 13;
                    int cellWidth_  = 95;
                    {   // для случайно существующих - тут по идее детаил/детаил должна быть - но проверить!!!
                        for (JRCrosstabCell cell : crosstab.getCellsList()) {
                            ((JRDesignCrosstabCell) cell).setHeight(cellHeight_);
                            ((JRDesignCrosstabCell) cell).setWidth(cellWidth_);
                            cell.getContents().getPropertiesMap().setProperty("com.jaspersoft.studio.layout", "com.jaspersoft.studio.editor.layout.FreeLayout");
                        }
                        // итоги колонок
                        for (JRCrosstabColumnGroup col : crosstab.getColumnGroupsList()) {
                            if (crosstab.getCellsList().stream().filter(a -> a.getRowTotalGroup() == null && a.getColumnTotalGroup() != null && a.getColumnTotalGroup().equals(col.getName())).findFirst().isEmpty()) {
                                JRDesignCrosstabCell cell_ = new JRDesignCrosstabCell();
                                //<property name="com.jaspersoft.studio.layout" value="com.jaspersoft.studio.editor.layout.FreeLayout"/>
                                cell_.setColumnTotalGroup(col.getName());
                                cell_.setHeight(cellHeight_);
                                cell_.setWidth(cellWidth_);
                                cell_.getContents().getPropertiesMap().setProperty("com.jaspersoft.studio.layout", "com.jaspersoft.studio.editor.layout.FreeLayout");
                                crosstab.addCell(cell_);
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

                  ///////////////////////////
                  // Заполним заголовки групп
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
                       JRCrosstabRowGroup lastJRCrosstabRowGroup = null;
                        int j = 0;
                        for (JRCrosstabRowGroup row : crosstab.getRowGroupsList()) {
                            row.getTotalHeader().getPropertiesMap().setProperty("com.jaspersoft.studio.layout", "com.jaspersoft.studio.editor.layout.FreeLayout");
                            int j_ = j;

                            JRDesignTextField nText = new JRDesignTextField();
                            nText.setHeight(cellHeight_);
                            nText.setWidth(10);
                            // если вывода нет то отметим как минус $V{lev4dimNomenklCostGroupP}==null||$V{lev4dimNomenklCostGroupP}.isEmpty()?-4:4
                            if (lastJRCrosstabRowGroup == null) {
                                nText.setExpression(new JRDesignExpression(Integer.toString(j)));
                            }
                            else {
                                nText.setExpression(new JRDesignExpression(
                                        formatValueExpr(rowGroupNameStr.get(j - 1)).concat("==null").concat("?").concat(Integer.toString(-j)).concat(":").concat(Integer.toString(j))
                                )); //formatValueExpr(rowGroupNameStr.get(j - 1))
                            }

                            nText.setStyle( Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG".concat(rowGroupStyleStr.get(j_)))).findFirst().get() ); // StyleG10
                            nText.setBlankWhenNull(true);
                            nText.setMode(ModeEnum.OPAQUE);
                            row.getTotalHeader().getChildren().add(nText);

                            nText = new JRDesignTextField();
                            nText.setHeight(cellHeight_);
                            nText.setWidth(10);
                            nText.setX(10);
                            nText.setExpression(new JRDesignExpression(Integer.toString(j)));
                            nText.setStyle( Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG".concat(rowGroupStyleStr.get(j_)))).findFirst().get() ); // StyleG10
                            nText.setBlankWhenNull(true);
                            nText.setMode(ModeEnum.OPAQUE);
                            row.getTotalHeader().getChildren().add(nText);

                            nText = new JRDesignTextField();
                            nText.setHeight(cellHeight_);
                            nText.setX(20);
                            nText.setWidth(300);
                            if (lastJRCrosstabRowGroup == null) {
                                nText.setExpression(new JRDesignExpression("\"Итого\""));  // первый итог идет вниз
                            } else {
                                nText.setExpression(new JRDesignExpression(formatValueExpr(rowGroupNameStr.get(j - 1))));
                            }
                            nText.setStyle( Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG".concat(rowGroupStyleStr.get(j_)))).findFirst().get() ); // StyleG10
                            nText.setBlankWhenNull(true);
                            nText.setMode(ModeEnum.OPAQUE);
                            row.getTotalHeader().getChildren().add(nText);

                            j++;
                            lastJRCrosstabRowGroup = row;
                        }

                        // самй нижний уровень - текущая переменная
                        if (lastJRCrosstabRowGroup != null) {
                            int j_ = j-1;

                            JRDesignTextField nText = new JRDesignTextField();
                            nText.setHeight(cellHeight_);
                            nText.setWidth(10);
                            nText.setExpression(new JRDesignExpression(Integer.toString(j)));
                            nText.setStyle( Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findAny().get() ); // StyleG10
                            nText.setBlankWhenNull(true);
                            nText.setMode(ModeEnum.OPAQUE);
                            lastJRCrosstabRowGroup.getHeader().getChildren().add(nText);

                            nText = new JRDesignTextField();
                            nText.setHeight(cellHeight_);
                            nText.setWidth(10);
                            nText.setX(10);
                            nText.setExpression(new JRDesignExpression(Integer.toString(j)));
                            nText.setStyle( Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findFirst().get() ); // StyleG10
                            nText.setBlankWhenNull(true);
                            nText.setMode(ModeEnum.OPAQUE);
                            lastJRCrosstabRowGroup.getHeader().getChildren().add(nText);

                            nText = new JRDesignTextField();
                            nText.setHeight(cellHeight_);
                            nText.setX(20);
                            nText.setWidth(300);
                            nText.setExpression(new JRDesignExpression(formatValueExpr(rowGroupNameStr.get(j_))));
                            nText.setStyle( Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findFirst().get() ); // StyleG10
                            nText.setBlankWhenNull(true);
                            nText.setMode(ModeEnum.OPAQUE);
                            lastJRCrosstabRowGroup.getHeader().getChildren().add(nText);
                        }
                    }

                  ///////////////////////////
                  // ЗАВЕДЕМ В ЯЧЕЙКАХ СТАНДАРТНЫЕ СУММЫ МЕР
                    //  закидываем в ячейки имена, шрифт и фон из стилей!!!
                    // формат ячеек - потом из настроек надо будет
                    // заголовки ячеек тоже подгружаем
                  ////////////////////////

                 // временно сработаем со старым списком
                    // 1 сделаем свой список

                    String[] columnsArr = colColumnEnabledStr.split(",");
                    int currentWidth = 0;
                    for (int s = 0; s < columnsArr.length; s++) {
                        if ( (!(columnsArr[s].equals("-")||columnsArr[s].equals("+")))&&columnsArr[s-1].equals("+") ) {
                            //поле
                            addFieldBigDecimal(columnsArr[s], fieldsList); // добавим поле
                            //мера
                            addMeasureSumBigDecimal(columnsArr[s], crosstab);
                            currentWidth += cellWidth_;
                            //и можно кидать во все ячейки кросстаба
                           for (JRCrosstabCell c : crosstab.getCellsList()) {
                               JRDesignCrosstabCell cell = (JRDesignCrosstabCell)c;
                               cell.setWidth(currentWidth);
                               JRDesignTextField expr = new JRDesignTextField();
                               expr.setBlankWhenNull(true);
                               expr.setWidth(cellWidth_);
                               expr.setX(currentWidth - cellWidth_);
                               // expr.setStyle(); - тут надо знать уровень группы - добавим позже из групп rowGroupStyleStr - тут код,
                               if (cell.getRowTotalGroup()!=null) {
                                   expr.setStyle(
                                       Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG".concat(
                                        rowGroupStyleStr.get(crosstab.getRowGroupIndicesMap().getOrDefault(cell.getRowTotalGroup(),crosstab.getRowGroupIndicesMap().size()-1))
                                       ))).findFirst().get()
                                   );
                               }
                               else {
                                   expr.setStyle(Arrays.stream(design.getStyles()).filter(a -> a.getName().equals("StyleG00")).findAny().get());
                               }
                               expr.setMode(ModeEnum.OPAQUE);
                               expr.setHeight(cellHeight_);
                               expr.setTextAdjust(TextAdjustEnum.SCALE_FONT);
                               expr.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
                               expr.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
                               expr.setExpression(new JRDesignExpression( formatValueExpr(columnsArr[s]).concat(".doubleValue()==0?null:").concat(formatValueExpr(columnsArr[s])) ));
                               //expr.setPatternExpression(); вроде есть переменная
                               expr.setPattern("#,##0.00");
                               cell.getContents().getChildren().add(expr);
                           }

                        }
                    }
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