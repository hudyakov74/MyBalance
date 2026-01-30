import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCol;

import javax.lang.model.util.ElementScanner6;
import java.io.*;
import java.sql.SQLException;
import java.util.*;

import static java.lang.Math.abs;


//https://poi.apache.org/components/spreadsheet/quick-guide.htm

public class XlsCreateRowOutlineV2 extends InternalAction {
    public XlsCreateRowOutlineV2(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    // XSSFSheet sheet;

    XSSFCell cellTarget;

    protected void deleteRow(XSSFSheet sheet, XSSFRow removingRow, int rowIndex) {
//   //sheet.getMergedRegions().remove(merge);
//             merge =  sheet.getMergedRegions().stream().filter(a -> a.isInRange(cellTarget)).findAny().orElse(null);
//                                            if (!(merge == null)) {
//                                                cellTarget = sheet.getRow(merge.getFirstRow()).getCell(merge.getFirstColumn());
        int lastRowNum = sheet.getLastRowNum();
        try {
            if (rowIndex >= 0 && rowIndex < lastRowNum) {
                sheet.shiftRows(rowIndex + 1, lastRowNum, -1);
            } else if (rowIndex == lastRowNum) {
                if (removingRow != null) {
                    sheet.removeRow(removingRow);
                }
            }
        }
        catch (Exception e) {
            if (rowIndex >= 0 && rowIndex < lastRowNum) {
                CellRangeAddress merge = sheet.getMergedRegions().stream().filter(a -> a.getFirstRow() == rowIndex).findAny().orElse(null);
                if (!(merge == null)) sheet.removeMergedRegion(sheet.getMergedRegions().indexOf(merge));

                sheet.shiftRows(rowIndex + 1, lastRowNum, -1);
            } else if (rowIndex == lastRowNum) {
                if (removingRow != null) {
                    sheet.removeRow(removingRow);
                }
            }
        }
    }

//    protected void deleteRowWithRemoveMerged(XSSFSheet sheet, XSSFRow removingRow, int rowIndex) {
//        int lastRowNum = sheet.getLastRowNum();
//
//        if (rowIndex >= 0 && rowIndex < lastRowNum) {
//            for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
//                CellRangeAddress merge = sheet.getMergedRegion(i);
//                if (merge.getFirstRow() == rowIndex) {
//                    sheet.removeMergedRegion(i);
//                }
//            }
//            sheet.shiftRows(rowIndex + 1, lastRowNum, -1);
//        }
//        if (rowIndex == lastRowNum) {
//            if (removingRow != null) {
//                sheet.removeRow(removingRow);
//            }
//        }
//    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        // костыль - выполняте доформатирование документа эксель
        // весия 2 - отказ от табуляторов в тексте отчета для смещений
        // 1. формирует иерархию отчета - создавая сворачиваемые группы/подгруппы
        // 2. выполняет фиксацию заголовка
        // 3. добавляет ко всем цифровым форматам - отрицательное красным
        // 4. удаляет специально помеченные строки из отчета - актуально для crosstab
        RawFileData f = (RawFileData) getParam(0, context);  // файл экселя
        Integer negativeRed = (Integer) getParam(1, context); //1 - отрицательное красным
        Integer fixColumn = (Integer) getParam(2, context);   // если больше 0 фиксирует столбцы
        Integer fixRow = (Integer) getParam(3, context);      // если >0 фиксирует строки
        Integer columnTreeIndex = (Integer) getParam(4, context); //   колонка в которой находится число - уровень иерархии строки


        // если уровень сделать отрицательным - строка будет удалена
        // сам уровень берется как abs от числа в ячейке
        Integer allLevelsRequired = (Integer) getParam(5, context); // инициация всех уровней согласно порядковому номеру уровня, или можно пропускать
        Integer columnForTab = (Integer) getParam(6, context); //  колонка стиль которой будем оформлять со смещением
        Integer columnTreeIndexIndention;
        try {
            columnTreeIndexIndention = (Integer) getParam(7, context); //   уровень раскрытия может не совпадать с уровнем иерархии - надо сделать отдельную колонку для этого
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            columnTreeIndexIndention = columnTreeIndex;
        };

        Integer fastFilterFirstRow = 0;
        try {
            fastFilterFirstRow = (Integer) getParam(8, context); //    строка с которой активируем быстрые фильтры
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            fastFilterFirstRow = 0;
        };
        Integer fastFilterLastColumn = 0;
        try {
            fastFilterLastColumn = (Integer) getParam(9, context); //  колонка до которой активируем быстрые фильтры
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            fastFilterLastColumn = 0;
        };

        Integer columnGroupIndexRow = 0;
        try {
            columnGroupIndexRow = -1 + (Integer) getParam(10, context); // строка горизонт иерархии
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            columnGroupIndexRow = -1;
        };


        Integer deleteModeRow;
        try {
            deleteModeRow = (Integer) getParam(11, context); // режим удаления строк 0 удалять 1- делать маленькую высоту
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            deleteModeRow = 0;
        };


        Map<Integer, Map<Integer, Integer>> ol = new HashMap<>();
        for (int i = 0; i < 20; i++) ol.put(i, new HashMap<>());
        int currentLevel = 0;
        //  int minCurrentLevel = 2;

        int rowLevel;
        int rowIndex = 0;
        XSSFRow currRow;
        CellRangeAddress merge;

        try {
            OPCPackage opcPackage = OPCPackage.open(f.getInputStream());
            XSSFWorkbook workbook = new XSSFWorkbook(opcPackage);
            XSSFCreationHelper richTextFactory =  workbook.getCreationHelper();

            // удалим дубли имен ячеек. смотрим только в прямом порядке - удаляем последние
            // для XSSFWorkbook - корректно, если повторение имен вообще корректная вещь
            java.util.List<? extends Name> names = workbook.getAllNames();
            ArrayList<String> namesInlist = new ArrayList<>();
            ArrayList<Name> forDelete = new ArrayList<>();
            for (Name name : names) {
                if (namesInlist.contains(name.getNameName())) {
                    forDelete.add(name); //
                } else {
                    namesInlist.add(name.getNameName());
                }
            }
            for (int i = forDelete.size() - 1; i >= 0; i--) workbook.removeName(forDelete.get(i));
            //

            //--
            XSSFSheet sheet;
            List<XSSFRow> copyRows = new ArrayList<>();
            CellCopyPolicy policyRow = new CellCopyPolicy.Builder().cellFormula(true).cellStyle(true)
                                                      .cellValue(true).mergedRegions(false)
                                                      .copyHyperlink(true).rowHeight(true).condenseRows(true).mergeHyperlink(true).build();
            int countWorbooks = workbook.getNumberOfSheets();



            // удаление ненужных строк через копирование
            // простое удаление долгое - копируем в новый лист со смещением 10000 - это глюк бибиотеки
            // потом возвращаем назад - тогда форматы и оформление шапки не слетает
            for (int ii = 0; ii < countWorbooks; ii++) {
                copyRows.clear();
                boolean toDelete = false;
                sheet = workbook.getSheetAt(ii);
                for (rowIndex = 0; sheet.getLastRowNum() >= rowIndex; rowIndex++)
                {
                     currRow = sheet.getRow(rowIndex);
                     if (
                         (currRow != null
                         &&
                         currRow.getCell(columnTreeIndex) != null
                         &&
                         currRow.getCell(columnTreeIndex).getCellType() == CellType.NUMERIC
                         &&
                         currRow.getCell(columnTreeIndex).getNumericCellValue() < 0)
                     ){
                         toDelete = true;
                     }
                     else {
                         copyRows.add(currRow);
                    }
                }
                if (toDelete && copyRows.size() > 0) {
                    XSSFSheet newSheet = workbook.createSheet();
                    newSheet.createRow(1);

                    //newSheet.copyRowFrom(srcRow, new CellCopyPolicy());
                    newSheet.copyRows(copyRows, 10000, policyRow);
                    copyRows.clear();
                    for (rowIndex = 10000; newSheet.getLastRowNum() >= rowIndex; rowIndex++)
                    {
                        copyRows.add(newSheet.getRow(rowIndex));
                    }
                    for (int x = 0; sheet.getLastRowNum() >= x; x++) {
                       if ( sheet.getRow(x) != null) { sheet.removeRow(sheet.getRow(x)); };
                    }
                    sheet.copyRows( copyRows, 0, policyRow);
                    workbook.removeSheetAt(countWorbooks);
                }
            }


            for (int ii = 0; ii < workbook.getNumberOfSheets(); ii++) {
                sheet = workbook.getSheetAt(ii);
                sheet.setRowSumsRight(false);
                // ((XSSFSheet)sheet).getColumnOutlineLevel();
                try {
                    if (fastFilterLastColumn != null && fastFilterLastColumn > 0)
                        sheet.setAutoFilter(new CellRangeAddress(fastFilterFirstRow, sheet.getLastRowNum(), 0, fastFilterLastColumn));
                }
                  catch(IllegalArgumentException e) { // не удалось
                }
                int currentMaxLevel = 0;
                for (rowIndex = 0; sheet.getLastRowNum() > rowIndex; rowIndex++)
                {
                    currRow = sheet.getRow(rowIndex);
                    //!!! подрезка высоких строк в шапках
                    if (currRow != null && rowIndex < 50 && currRow.getHeight()>(20*150)) {
                        currRow.setHeight((short)(85*20));
                    }
                    //!!!
                    if (currRow != null && currRow.getCell(columnTreeIndex) != null ) {
                        if (currRow.getCell(columnTreeIndex).getCellType() == CellType.NUMERIC
                            && abs(currRow.getCell(columnTreeIndex).getNumericCellValue()) >= 0) {
                            rowLevel = abs((int) currRow.getCell(columnTreeIndex).getNumericCellValue());
                            if (currentLevel < rowLevel) {
                                // уровень повышен
                                while (currentLevel < rowLevel) {
                                    currentMaxLevel++;
                                    ol.get(currentLevel).put(0, 1);
                                    ol.get(currentLevel).put(1, rowIndex);
                                    if (allLevelsRequired == 1) {
                                        currentLevel++; //=rowLevel;
                                    } else {
                                        currentLevel = rowLevel;
                                    }
                                }
                            }
                            // уровень понижен - сброс уровня
                            while (currentLevel > rowLevel && currentLevel > 0) {
                                currentLevel--;
                                if (ol.get(currentLevel).containsKey(0) && (ol.get(currentLevel).get(0) == 1)) {
                                    currentMaxLevel--;
                                    ol.get(currentLevel).put(0, 0);
                                    if ( currentMaxLevel < 7 && currentMaxLevel >= 0) { // max for excel - 8 levels и 0 не закрываем
                                        sheet.groupRow(ol.get(currentLevel).get(1), rowIndex - 1);
                                    }
                                }
                            }
                        }
                        ///  ограничим обработку шапки 50 строками
                        else if ( rowIndex < 50
                                && currRow.getCell(columnTreeIndex).getCellType() == CellType.STRING
                                && currRow.getCell(columnTreeIndex).getStringCellValue().contentEquals("Comment")) {
                             for (Cell cellComment : currRow) {
                                    if (cellComment != null
                                            && cellComment.getCellType() == CellType.STRING
                                            && !cellComment.getStringCellValue().isEmpty()
                                            && sheet.getRow(rowIndex - 1) != null
                                            && sheet.getRow(rowIndex - 1).getCell(cellComment.getColumnIndex()) != null) {

                                        cellTarget = (XSSFCell) sheet.getRow(rowIndex - 1).getCell(cellComment.getColumnIndex());
                                        merge = sheet.getMergedRegions().stream().filter(a -> a.isInRange(cellTarget)).findAny().orElse(null);
                                        if (!(merge == null)) {
                                            cellTarget = (XSSFCell) sheet.getRow(merge.getFirstRow()).getCell(merge.getFirstColumn());
                                            Drawing<?> drawing = sheet.createDrawingPatriarch();
                                            ClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, cellComment.getColumnIndex(), rowIndex, cellComment.getColumnIndex() + 3, rowIndex + 10);
                                            Comment comment1 = drawing.createCellComment(anchor);
                                            RichTextString rtf1 = richTextFactory.createRichTextString(cellComment.getStringCellValue());
                                            comment1.setString(rtf1);
                                            cellTarget.setCellComment(comment1);
                                        }
                                    }
                             }
                            deleteRow(sheet, currRow, rowIndex);
                            rowIndex--;
                        }
                    }
                }
                rowLevel = 0;
                // уровень понижен - сброс уровня
                while (currentLevel > rowLevel && currentLevel >= 0) {
                    currentLevel--;
                    if (ol.get(currentLevel).containsKey(0) && (ol.get(currentLevel).get(0) == 1)) {
                        ol.get(currentLevel).put(0, 0);
                        currentMaxLevel--;
                        if ( currentMaxLevel < 7 && currentMaxLevel > 0) {// max for excel - 8 levels
                            sheet.groupRow(ol.get(currentLevel).get(1), rowIndex - 1);
                        }
                    }
                }

                // все табуляторы в тексте отчета заменить на смещения
                // внимание: СТИЛИ для каждого уровня ДОЛЖНЫ БЫТЬ СВОИ - тогда работает
                // 2. из-за различных уровней групп - стили "поднимаются" с нижних групп () и требуется коррекция
                // 3. если составляется отчет из нескольких кросстабов то стили ломаются
                Map <XSSFCellStyle,List<XSSFCellStyle>> newStyles = new HashMap<>(); // сохраним новые стили для повторного использования
                List<XSSFCellStyle> currList;
                Map<Integer,XSSFCellStyle>  levelStyle = new HashMap<>(); // если у строки есть уровень - то родительский стиль используем тот, который первый раз встретился на конкретном уровне
                XSSFCellStyle currStyle;

                if (columnForTab >= 0 && columnTreeIndexIndention >= 0) {


//

                    String hexColorCrosstabLabelCurrent = "";
                    XSSFCell cell_;
                    XSSFCell cellTree_;
                    for (Row row_ : sheet) {

                        // обновим стиль уровня
                        if (row_.getCell(columnTreeIndexIndention).getCellType() == CellType.NUMERIC
                            && abs(row_.getCell(columnTreeIndexIndention).getNumericCellValue()) >= 0) {

                            //если разделитель кросстабов то сбросим стили - разделителем сделаем инкремент цвета фона ячейки индекса
                            if (        row_.getCell(columnTreeIndex) != null
                                    &&  row_.getCell(columnTreeIndex).getCellStyle() != null
                                    &&  row_.getCell(columnTreeIndex).getCellStyle().getFillForegroundColorColor() != null) {

                                String hexColorCrosstabLabel = ((XSSFColor) row_.getCell(columnTreeIndex).getCellStyle().getFillForegroundColorColor()).getARGBHex();
                                if (!hexColorCrosstabLabel.equals(hexColorCrosstabLabelCurrent)) {
                                    hexColorCrosstabLabelCurrent = hexColorCrosstabLabel;
                                    levelStyle.clear();
                                }
                            }

                            rowLevel = abs((int) row_.getCell(columnTreeIndexIndention).getNumericCellValue());
                            currStyle = levelStyle.get(rowLevel);
                            if ( currStyle == null ) {
                                currStyle = (XSSFCellStyle) row_.getCell(columnTreeIndexIndention).getCellStyle();
                                levelStyle.put(rowLevel, currStyle);
                            }

                        } else {
                            currStyle = null;
                        }
                        XSSFCellStyle  newStyle = null;
                        if ( currStyle != null ) {
                            for (  Cell  cell__:row_ ) {
                                if (cell__.getColumnIndex() !=  columnTreeIndex) {
                                    currList = newStyles.get(cell__.getCellStyle());
                                    if (currList == null) {
                                        currList = new ArrayList<>();
                                        newStyles.put((XSSFCellStyle) cell__.getCellStyle(), currList);
                                    }
                                    if (currStyle.getFillForegroundColorColor() != null && cell__.getCellStyle().getFillForegroundColorColor() != null
                                            && !currStyle.getFillForegroundColorColor().getARGBHex().equals(((XSSFCellStyle) cell__.getCellStyle()).getFillForegroundColorColor().getARGBHex())) {
                                        XSSFCellStyle cs = currStyle;
                                        Optional<XSSFCellStyle> newStyleFf = currList.stream().filter(a -> a.getFillForegroundColorColor().getARGBHex().equals((cs).getFillForegroundColorColor().getARGBHex())).findFirst();
                                        if (newStyleFf.isEmpty()) {
                                            newStyle = ((XSSFCell) cell__).getCellStyle().copy();
                                            currList.add(newStyle);
                                            XSSFColor color = new XSSFColor();
                                            color.setARGBHex(currStyle.getFillForegroundColorColor().getARGBHex());
                                            newStyle.setFillForegroundColor(color);
                                        } else {
                                            newStyle = newStyleFf.get();
                                        }
                                        cell__.setCellStyle(newStyle);
                                    }
                                }
                           }
                        }




                        // далее по стилям в ячейках с иерархией
                        cell_ = (XSSFCell) row_.getCell(columnForTab, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        cellTree_ = (XSSFCell) row_.getCell(columnTreeIndexIndention, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        if (!(cell_ == null) && !(cellTree_ == null)) {
                            if   (cell_.getCellType() == CellType.STRING
                                    &&
                                    cellTree_.getCellType() == CellType.NUMERIC
                                    &&
                                    //cell_.getCellStyle().getIndention() == (short) 0
                                    //cell_.getCellStyle().getIndention() == (short) 0
                                    //&&
                                    abs(cellTree_.getNumericCellValue()) >= 0
                            ) {
                                // корректируем стиль под текущйи уровень
                                if   (currStyle != null && cell_.getCellStyle() !=  currStyle) {
                                     cell_.setCellStyle(currStyle);
                                }
                                //cell_.getCellStyle()

                                if (cell_.getCellStyle().getIndention() == (short) 0) {
                                    cell_.getCellStyle().setIndention((short) (abs(cellTree_.getNumericCellValue())));
                                }
                                else if (cell_.getCellStyle().getIndention()  != (short) (abs(cellTree_.getNumericCellValue()))) {
                                    currList = newStyles.get(cell_.getCellStyle());
                                    if ( currList == null) {
                                        currList = new ArrayList<>();
                                        newStyles.put(cell_.getCellStyle(), currList);
                                    }
                                    short wth = (short) (abs(cellTree_.getNumericCellValue()));

                                    if (!currList.stream().filter(a -> a.getIndention() == wth).findFirst().isEmpty())
                                    {
                                        newStyle = currList.stream().filter(a -> a.getIndention() == wth).findFirst().get();
                                    }
                                    else {
                                        newStyle = cell_.getCellStyle().copy();
                                        currList.add(newStyle);
                                    }
                                    cell_.setCellStyle(newStyle);
                                    cell_.getCellStyle().setIndention((short) (abs(cellTree_.getNumericCellValue())));
                                }


                            }

                        }


                    }
                }

                // все стили с номерами пометим - отрицательное красным
                if (negativeRed == 1) {
                    String format;
                    for (Row row_ : sheet) {
                        for (Cell cell_ : row_) {
                            if (cell_.getCellType() == CellType.NUMERIC || cell_.getCellType() == CellType.FORMULA) {
                                format = cell_.getCellStyle().getDataFormatString();
                                if (format.contains("#,##0") && !format.contains("RED")) {
                                    format = format.concat(";[RED]-").concat(format);
                                    cell_.getCellStyle().setDataFormat(workbook.createDataFormat().getFormat(format));
                                }
                            }
                        }
                    }
                }

                if (fixRow > 0 || fixColumn > 0) {
                    sheet.createFreezePane(fixColumn, fixRow);
                }
                if (columnTreeIndex > 0) {
                    sheet.setColumnHidden(columnTreeIndex, true);
                }
                if (columnTreeIndexIndention > 0 && columnTreeIndexIndention != columnTreeIndex) {
                    sheet.setColumnHidden(columnTreeIndexIndention, true);
                }

                if  (columnGroupIndexRow >= 0) { // zero based -- спустимся на уровень ниже -sheet.groupColumn глючный
                    int lev;
                    Row row = sheet.getRow(columnGroupIndexRow);
                    if (row != null)
                        for (Cell cell_ : row) {
                            if (cell_ != null && cell_.getCellType() == CellType.NUMERIC) {
                                lev = (int) abs(cell_.getNumericCellValue());
                                if (lev > 0 && lev < 8 ) {
                                   CTCol col =  sheet.getColumnHelper().getColumn(cell_.getColumnIndex(), false);
                                   if (col != null) col.setOutlineLevel((short) lev);
                                }
                            }
                    }
                }

           }


           // OutputStream os1 = new ByteArrayOutputStream();
           ByteArrayOutputStream os = new ByteArrayOutputStream();

           workbook.write(os);
           RawFileData rf = new RawFileData(os);
           //rf.write(os);
           findProperty("fileXLS").change(rf, context);

        } catch (IOException | ScriptingErrorLog.SemanticErrorException | InvalidFormatException
                e) {
            e.printStackTrace();
            try {
                findProperty("fileXLSError").change(e.getMessage(), context);
            } catch (ScriptingErrorLog.SemanticErrorException ex) {
                e.printStackTrace();
            }
        }
    }
}
