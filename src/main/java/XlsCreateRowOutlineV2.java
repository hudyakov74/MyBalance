import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static java.lang.Math.abs;
import static java.lang.Math.max;

//https://poi.apache.org/components/spreadsheet/quick-guide.htm

public class XlsCreateRowOutlineV2 extends InternalAction {
    public XlsCreateRowOutlineV2(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    XSSFSheet sheet;

    protected void deleteRow(XSSFSheet sheet,XSSFRow removingRow, int rowIndex) {
             int lastRowNum=sheet.getLastRowNum();
             if(rowIndex >= 0 && rowIndex < lastRowNum){
                 for(int i = 0; i < sheet.getNumMergedRegions(); i++) {
                       CellRangeAddress merge = sheet.getMergedRegion(i);
                       if(merge.getFirstRow() == rowIndex) {
                            sheet.removeMergedRegion(i);
                       }
                 }
                 sheet.shiftRows(rowIndex+1,lastRowNum, -1);
             }
             if(rowIndex==lastRowNum){
                    if(removingRow != null){
                        sheet.removeRow( removingRow );
                    }
             }
    }
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
        Integer columnTreeIndex = (Integer) getParam(4, context); // колонка в которой находится число - уровень иерархии строки
        // если уровень сделать отрицательным - строка будет удалена
        // сам уровень берется как abs от числа в ячейке
        Integer allLevelsRequired = (Integer) getParam(5, context); // инициация всех уровней согласно порядковому номеру уровня, или можно пропускать
        Integer columnForTab = (Integer) getParam(6, context); //  колонка стиль которой будем оформлять со смещением

        Map<Integer, Map<Integer, Integer>> ol = new HashMap<>();
        for (int i = 0; i < 20; i++) ol.put(i, new HashMap<>());
        int currentLevel = 0;
        int rowLevel = 0;
        int rowIndex = 0;
        Cell cell;
        try {

            XSSFWorkbook workbook = new XSSFWorkbook(f.getInputStream());
            XSSFCreationHelper richTextFactory = workbook.getCreationHelper();

            // удалим дубли именjd смотрим только в прямом порядке - удаляем последние
            java.util.List<? extends Name> names = workbook.getAllNames();
            ArrayList<String> namesInlist = new ArrayList<>();
            ArrayList<Name> forDelete = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                Name name = names.get(i);
                if (namesInlist.contains(name.getNameName())) {
                    forDelete.add(name); //
                } else {
                    namesInlist.add(name.getNameName());
                }
            }
            for (int i = forDelete.size() - 1; i >= 0; i--) workbook.removeName(forDelete.get(i));

            Iterator<Sheet> sheetIterator = workbook.sheetIterator();
            //sheet = workbook.getSheetAt(0);
            while (sheetIterator.hasNext()) {
                Sheet sheet1 = sheetIterator.next();
                sheet = workbook.getSheetAt(workbook.getSheetIndex(sheet1));
                for (rowIndex = 0; sheet.getLastRowNum() > rowIndex; rowIndex++) {
                    XSSFRow removingRow = sheet.getRow(rowIndex);

                    if (removingRow != null) {
                        if (removingRow.getCell(columnTreeIndex)!=null &&
                                removingRow.getCell(columnTreeIndex).getCellType() == CellType.NUMERIC
                                        && abs(removingRow.getCell(columnTreeIndex).getNumericCellValue()) >= 0
                        )
                        {
                            rowLevel = abs((int) removingRow.getCell(columnTreeIndex).getNumericCellValue());
                            if (currentLevel < rowLevel) {
                                // уровень повышен
                                while (currentLevel < rowLevel) {
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
                            while (currentLevel > rowLevel) {
                                currentLevel--;
                                if (ol.get(currentLevel).containsKey(0) && (ol.get(currentLevel).get(0) == 1)) {
                                    ol.get(currentLevel).put(0, 0);
                                    sheet.groupRow(ol.get(currentLevel).get(1), rowIndex - 1);
                                }
                            }
                            // при отрицательном значении индекса - удаляем всю строчку
                            if (removingRow.getCell(columnTreeIndex).getNumericCellValue() < 0) {
                                deleteRow(sheet, removingRow, rowIndex);
                                rowIndex--;
//                                 int lastRowNum=sheet.getLastRowNum();
//                                 if(rowIndex >= 0 && rowIndex < lastRowNum){
//                                     for(int i = 0; i < sheet.getNumMergedRegions(); i++) {
//                                           CellRangeAddress merge = sheet.getMergedRegion(i);
//                                           if(merge.getFirstRow() == rowIndex) {
//                                                sheet.removeMergedRegion(i);
//                                           }
//                                     }
//
//                                     sheet.shiftRows(rowIndex+1,lastRowNum, -1);
//                                 }
//                                 if(rowIndex==lastRowNum){
//                                        if(removingRow!=null){
//                                            sheet.removeRow(removingRow);
//                                        }
//                                 }
                            }
                        }
                        else if (removingRow.getCell(columnTreeIndex)!=null &&
                                removingRow.getCell(columnTreeIndex).getCellType() == CellType.STRING
                                        && removingRow.getCell(columnTreeIndex).getStringCellValue().contentEquals("Comment") ) {
                            Iterator<Cell> cellIterator  =  removingRow.cellIterator();
                                while (cellIterator.hasNext()) {
                                    Cell cellComment = cellIterator.next();
                                    Cell cellTarget  ;

                                    if (cellComment != null
                                            && cellComment.getCellType() == CellType.STRING
                                            && cellComment.getStringCellValue().length() > 0
                                            && sheet.getRow(rowIndex-1) != null
                                            && sheet.getRow(rowIndex-1).getCell(cellComment.getColumnIndex()) != null) {

                                                cellTarget = sheet.getRow(rowIndex-1).getCell(cellComment.getColumnIndex());

                                                  for(int i = 0; i < sheet.getNumMergedRegions(); i++) {
                                                        if (sheet.getMergedRegion(i).isInRange(cellTarget)) {
                                                            cellTarget =sheet.getRow(sheet.getMergedRegion(i).getFirstRow()).getCell(sheet.getMergedRegion(i).getFirstColumn());
                                                            break;
                                                        }
                                                 }

                                                 XSSFDrawing drawing = sheet.createDrawingPatriarch();
                                                 XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, cellComment.getColumnIndex(), rowIndex, cellComment.getColumnIndex() + 3, rowIndex + 10);
                                                 XSSFComment comment1 = drawing.createCellComment(anchor);
                                                 XSSFRichTextString rtf1 = richTextFactory.createRichTextString(cellComment.getStringCellValue());
                                                 comment1.setString(rtf1);
                                                 cellTarget.setCellComment(comment1);
                                    }
                                }
                                deleteRow(sheet, removingRow, rowIndex);
                                rowIndex--;
                        }
                    }


                }
                rowLevel = 0;
                // уровень понижен - сброс уровня
                while (currentLevel > rowLevel) {
                    currentLevel--;
                    if (ol.get(currentLevel).containsKey(0) && (ol.get(currentLevel).get(0) == 1)) {
                        ol.get(currentLevel).put(0, 0);
                        sheet.groupRow(ol.get(currentLevel).get(1), rowIndex - 1);
                    }
                }

                XSSFCell cellXSSF;

                // все табуляторы в тексте отчета заменить на смещения
                // внимание: СТИЛИ для каждого уровня ДОЛЖНЫ БЫТЬ СВОИ - тогда работает
                for (rowIndex = 0; sheet.getLastRowNum() > rowIndex; rowIndex++) {
                    if (sheet.getRow(rowIndex) != null) {
                        Iterator<Cell> cellIterator = sheet.getRow(rowIndex).cellIterator();
                        while (cellIterator.hasNext()) {
                            cell = cellIterator.next();
                            cellXSSF = (XSSFCell) cell;
                            if (cellXSSF.getCellType() == CellType.STRING
                                    &&
                                    cellXSSF.getColumnIndex() == columnForTab
                                    &&
                                    cellXSSF.getCellStyle().getIndention() == (short) 0
                                    &&
                                    sheet.getRow(rowIndex).getCell(columnTreeIndex) != null
                                    &&
                                    sheet.getRow(rowIndex).getCell(columnTreeIndex).getCellType() == CellType.NUMERIC
                                    &&
                                    abs(sheet.getRow(rowIndex).getCell(columnTreeIndex).getNumericCellValue()) >= 0
                            ) {
                                cellXSSF.getCellStyle().setIndention((short) (abs(sheet.getRow(rowIndex).getCell(columnTreeIndex).getNumericCellValue())));
                            }

                            //         else if (cellXSSF.getCellType() == CellType.FORMULA) {


                            //       cellXSSF.setCellFormula(cellXSSF.getStringCellValue());
                            //    }
                            else if (negativeRed == 1 && (cellXSSF.getCellType() == CellType.NUMERIC || cellXSSF.getCellType() == CellType.FORMULA)) {
                                //int s = 1;
                                String format = cellXSSF.getCellStyle().getDataFormatString();
                                if (format.contains("#,##0") && !format.contains("RED")) {
                                    format = format.concat(";[RED]-").concat(format);
                                    cellXSSF.getCellStyle().setDataFormat(workbook.createDataFormat().getFormat(format));
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
                }
            }


            OutputStream os = new ByteArrayOutputStream();
            workbook.write(os);
            RawFileData rf = new RawFileData((ByteArrayOutputStream) os);
            findProperty("fileXLS").change(rf, context);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            e.printStackTrace();
        }


    }
}
