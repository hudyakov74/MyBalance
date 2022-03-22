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
        import org.apache.poi.xssf.usermodel.*;

        import java.io.ByteArrayOutputStream;
        import java.io.IOException;
        import java.io.OutputStream;
        import java.sql.SQLException;
        import java.util.HashMap;
        import java.util.Iterator;
        import java.util.Map;

        import static java.lang.Math.abs;
        import static java.lang.Math.max;

        //https://poi.apache.org/components/spreadsheet/quick-guide.html
        public class XlsCreateRowOutline  extends InternalAction {
    public XlsCreateRowOutline(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }
    XSSFSheet sheet;

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        // костыль - выполняте доформатирование документа эксель
        // 1. формирует иерархию отчета - создавая сворачиваемые группы/подгруппы
        // 2. выполняет фиксацию заголовка
        // 3. добавляет ко всем цифровым форматам - отрицательное красным
        // 4. удаляет специально помеченные строки из отчета - актуально для crosstab
        RawFileData f =  (RawFileData)getParam(0, context); // файл экселя
        Integer negativeRed = (Integer)getParam(1, context); //1 - отрицательное красным
        Integer fixRow = (Integer)getParam(3, context); // если >0 фиксирует строки
        Integer fixColumn = (Integer)getParam(2, context); // если больше 0 фиксирует столбцы
        Integer columnTreeIndex = (Integer)getParam(4, context); // колонка в которой находится число - уровень иерархии строки
                                                                   // если уровень сделать отрицательным - строка будет удалена
                                                                   // сам уровень берется как abs от числа в ячейке
        Integer allLevelsRequired = (Integer)getParam(5, context); // инициация всех уровне согласно порядковому номеру уровня, или можно пропускать


        Map<Integer,Map<Integer,Integer>> ol = new HashMap<>();
        for (int i =0;i<20;i++)  ol.put(i,new HashMap<>());
        int currentLevel=0;
        int rowLevel=0;
        int rowIndex=0;
        Cell cell;
        try {

            XSSFWorkbook workbook = new XSSFWorkbook(f.getInputStream() );
            sheet = workbook.getSheetAt(0);

    for (rowIndex = 0; sheet.getLastRowNum() > rowIndex; rowIndex++) {
        XSSFRow removingRow = sheet.getRow(rowIndex);

        if (removingRow != null) {
            if (
                    removingRow.getCell(columnTreeIndex).getCellType() == CellType.NUMERIC
                            && abs(removingRow.getCell(columnTreeIndex).getNumericCellValue()) >= 0
                //  &&  abs(removingRow.getCell(columnTreeIndex).getNumericCellValue())<
            ) {
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
                    sheet.removeRow(removingRow);
                    sheet.shiftRows(rowIndex + 1, sheet.getLastRowNum(), -1);
                    rowIndex--;
                }
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
        Iterator<Cell> cellIterator = sheet.getRow(rowIndex).cellIterator();
        while (cellIterator.hasNext()) {
            cell = cellIterator.next();
            cellXSSF = (XSSFCell)cell;
            if (cellXSSF.getCellType() == CellType.STRING
                    && StringUtils.countMatches(cellXSSF.getStringCellValue(), "\t") > 0) {

                String str = cellXSSF.getStringCellValue();
                if (cellXSSF.getCellStyle().getIndention() == (short) 0) {
                    cellXSSF.getCellStyle().setIndention((short) (StringUtils.countMatches(str, "\t")));
                }
               // cellXSSF.setCellFormula();
                // cellXSSF.setCellType(CellType.STRING);
                // cellXSSF.setCellValue( StringUtils.replace(str, "\t", ""));
                //  ms office и так удаляет табуляторы в начале. open office не удаляет
                //  но setCellType ломает документ для ms office а без setCellType в open office - пустые поля
            } else if (cellXSSF.getCellType() == CellType.FORMULA) {


              //       cellXSSF.setCellFormula(cellXSSF.getStringCellValue());
            } else   if (negativeRed == 1 && cellXSSF.getCellType() == CellType.NUMERIC) {
                int s = 1;
                String format = cellXSSF.getCellStyle().getDataFormatString();
                if (format.contains("#,##0") && !format.contains("RED")) {
                    format = format.concat(";[RED]-").concat(format);
                    cellXSSF.getCellStyle().setDataFormat(workbook.createDataFormat().getFormat(format));
                }
            }
        }
    }

         if(fixRow>0 || fixColumn>0){
            sheet.createFreezePane(fixColumn,fixRow);
         }
         if(columnTreeIndex>0) {
             sheet.setColumnHidden(columnTreeIndex,true);
         }



        OutputStream os = new ByteArrayOutputStream();
        workbook.write(os);
        RawFileData  rf = new RawFileData((ByteArrayOutputStream)os);
        findProperty("fileXLS").change(rf, context);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            e.printStackTrace();
        }
    }
}
//
//XSSFCellStyle cellStyle = workbook.createCellStyle();
//   cellStyle.setFillForegroundColor(color);
//   cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//   cellStyle.setAlignment(HorizontalAlignment.RIGHT);
//
//   XSSFSheet sheet = workbook.createSheet();
//   XSSFCell cell = sheet.createRow(0).createCell(0);
//   cell.setCellValue("A1");
//   cell.setCellStyle(cellStyle);