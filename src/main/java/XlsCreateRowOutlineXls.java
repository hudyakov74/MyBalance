import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.poi.hssf.usermodel.HSSFCreationHelper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.abs;


//https://poi.apache.org/components/spreadsheet/quick-guide.htm

public class XlsCreateRowOutlineXls extends InternalAction {
    public XlsCreateRowOutlineXls(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    // XSSFSheet sheet;

    Cell cellTarget;

    protected void deleteRow(Sheet sheet, Row removingRow, int rowIndex) {
//   //sheet.getMergedRegions().remove(merge);
//             merge =  sheet.getMergedRegions().stream().filter(a -> a.isInRange(cellTarget)).findAny().orElse(null);
//                                            if (!(merge == null)) {
//                                                cellTarget = sheet.getRow(merge.getFirstRow()).getCell(merge.getFirstColumn());
        int lastRowNum = sheet.getLastRowNum();
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

        Map<Integer, Map<Integer, Integer>> ol = new HashMap<>();
        for (int i = 0; i < 20; i++) ol.put(i, new HashMap<>());
        int currentLevel = 0;
        int rowLevel;
        int rowIndex = 0;
        Row removingRow;
        CellRangeAddress merge;

        try {

           //  OPCPackage opcPackage = OPCPackage.open(f.getInputStream());
             HSSFWorkbook workbook = new HSSFWorkbook(f.getInputStream());
             HSSFCreationHelper richTextFactory =  workbook.getCreationHelper();
//               XSSFWorkbook workbook = new XSSFWorkbook(f.getInputStream());
//               XSSFCreationHelper richTextFactory =  workbook.getCreationHelper();

            // удалим дубли имен ячеек. смотрим только в прямом порядке - удаляем последние
            // !!! для HSSF - некорректное удаление
//            java.util.List<? extends Name> names = workbook.getAllNames();
//            ArrayList<String> namesInlist = new ArrayList<>();
//            ArrayList<Name> forDelete = new ArrayList<>();
//            for (Name name : names) {
//                if (namesInlist.contains(name.getNameName())) {
//                    forDelete.add(name); //
//                } else {
//                    namesInlist.add(name.getNameName());
//                }
//            }
//            for (int i = forDelete.size() - 1; i >= 0; i--) workbook.removeName(forDelete.get(i));
            //--

            for (Sheet sheet : workbook) {
                //for (Row removingRow : sheet)
                for (rowIndex = 0; sheet.getLastRowNum() > rowIndex; rowIndex++)
                {   removingRow = sheet.getRow(rowIndex);
                    if (removingRow != null) {
                        if (    removingRow.getCell(columnTreeIndex) != null
                                &&
                                removingRow.getCell(columnTreeIndex).getCellType() == CellType.NUMERIC
                                &&
                                abs(removingRow.getCell(columnTreeIndex).getNumericCellValue()) >= 0)
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
                            }
                        } else if (removingRow.getCell(columnTreeIndex) != null
                                &&
                                removingRow.getCell(columnTreeIndex).getCellType() == CellType.STRING
                                &&
                                removingRow.getCell(columnTreeIndex).getStringCellValue().contentEquals("Comment"))
                        {
                            for (Cell cellComment : removingRow) {
                                {
                                    if (cellComment != null
                                            && cellComment.getCellType() == CellType.STRING
                                            && !cellComment.getStringCellValue().isEmpty()
                                            && sheet.getRow(rowIndex - 1) != null
                                            && sheet.getRow(rowIndex - 1).getCell(cellComment.getColumnIndex()) != null) {

                                        cellTarget = sheet.getRow(rowIndex - 1).getCell(cellComment.getColumnIndex());
                                        merge = sheet.getMergedRegions().stream().filter(a -> a.isInRange(cellTarget)).findAny().orElse(null);
                                        if (!(merge == null)) {
                                            cellTarget = sheet.getRow(merge.getFirstRow()).getCell(merge.getFirstColumn());
                                            Drawing<?> drawing = sheet.createDrawingPatriarch();
                                            ClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, cellComment.getColumnIndex(), rowIndex, cellComment.getColumnIndex() + 3, rowIndex + 10);
                                            Comment comment1 = drawing.createCellComment(anchor);
                                            RichTextString rtf1 = richTextFactory.createRichTextString(cellComment.getStringCellValue());
                                            comment1.setString(rtf1);
                                            cellTarget.setCellComment(comment1);
                                        }
                                    }
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

                // все табуляторы в тексте отчета заменить на смещения
                // внимание: СТИЛИ для каждого уровня ДОЛЖНЫ БЫТЬ СВОИ - тогда работает
                if (columnForTab >= 0 && columnTreeIndex >= 0) {
                    Cell cell_;
                    Cell cellTree_;
                    for (Row row_ : sheet) {
                        cell_ = row_.getCell(columnForTab, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        cellTree_ = row_.getCell(columnTreeIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        if (!(cell_ == null) && !(cellTree_ == null)) {
                            if (cell_.getCellType() == CellType.STRING
                                    &&
                                    cellTree_.getCellType() == CellType.NUMERIC
                                    &&
                                    cell_.getCellStyle().getIndention() == (short) 0
                                    &&
                                    abs(cellTree_.getNumericCellValue()) >= 0
                            ) {
                                cell_.getCellStyle().setIndention((short) (abs(cellTree_.getNumericCellValue())));
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
//                String s_ ;
//                  for (HSSFRow row_ : sheet) {
//                        for (HSSFCell cell_ : row_) {
//                         //   if (cell_.getCellType() == CellType.STRING && cell_.getStringCellValue().equals("=IFERROR(brdSum1,0)"))
//                            if (cell_.getCellType() == CellType.STRING && cell_.getStringCellValue().substring(0,1).equals("=") )  { //|| cell_.getCellType() == CellType.FORMULA) cell_.getCellType() == CellType.FORMULA
//                                 s_ = cell_.getStringCellValue().substring(1);
//                                 cell_.setCellValue("");
//                                 cell_.setCellFormula(s_);
//                                 cell_.setCellType(HSSFCell.FORMULA);
//
//                            }
//                        }
//                    }


                if (fixRow > 0 || fixColumn > 0) {
                    sheet.createFreezePane(fixColumn, fixRow);
                }
                if (columnTreeIndex > 0) {
                    sheet.setColumnHidden(columnTreeIndex, true);
                }

            }
           // OutputStream os1 = new ByteArrayOutputStream();
            ByteArrayOutputStream os = new ByteArrayOutputStream();

        //    workbook.write();
//SXSSFWorkbook workbook1 = new SXSSFWorkbook(workbook);

           workbook.write(os);
          // workbook.close();
         //   opcPackage.close();
          //  opcPackage.save(os1);
            // RawFileData r
           RawFileData rf = new RawFileData(os);
           //rf.write(os);
            findProperty("fileXLS").change(rf, context);

        } catch (IOException
                 | ScriptingErrorLog.SemanticErrorException
                e) {
            e.printStackTrace();
        }
    }
}
