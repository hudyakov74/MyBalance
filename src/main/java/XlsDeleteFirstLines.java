import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFName;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellFormulaType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;

public class XlsDeleteFirstLines extends InternalAction
{
    public XlsDeleteFirstLines(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        // удаление не производим- переносим заголовок в 0 строку и стираем ячейки между, иначе сохранить лист будет временной проблемой

        RawFileData f = (RawFileData) getParam(0, context);  // файл экселя
        String  countRowStr = (String) getParam(1, context);   // количество строк удаления (sheet:num,)
        try {
            XSSFWorkbook workbook  = new XSSFWorkbook(f.getInputStream());
            XSSFName nameOut;

            Sheet sheet;
            String sheets[] = countRowStr.split(",");
            for (String s : sheets) {
                String sheetName[] = s.split(":");
                try {
                    sheet = workbook.getSheetAt(Integer.parseInt(sheetName[0]) - 1);
                } catch (NumberFormatException e) {
                    sheet = workbook.getSheet(sheetName[0]);
                }
                try {
                        Integer countRow = Integer.parseInt(sheetName[1]);
                        if (sheet.getLastRowNum() > countRow) {
                            Row rowIn = sheet.getRow(0);
                            Row row   = sheet.getRow(countRow);
                            // очистим значения в 0 строке
                            for (Cell cell_ : rowIn) {
                                cell_.setCellType(CellType.STRING);
                                cell_.setCellValue("");
                            }
                            // перенесем заголовки в 0 строку
                            Cell cellIn;
                            for (Cell cell : row) {
                              cellIn = rowIn.getCell(cell.getColumnIndex());
                              if (cellIn == null) {
                                cellIn = row.createCell(cell.getColumnIndex());
                              }
                              cellIn.setCellType(CellType.STRING);
                              cellIn.setCellValue(cell.getStringCellValue());
                            }
                            // удалим все до данных
                            for (Integer rowIndex = 1; countRow >= rowIndex; rowIndex++) {
                                row = sheet.getRow(rowIndex);
                                for (Cell cell : row) {
                                    cell.setCellType(CellType.STRING);
                                    cell.setCellValue("");
                                }
                            }
                        }
                    } catch (NumberFormatException e)
                {
                }
            }

            OutputStream os = new ByteArrayOutputStream();
            workbook.write(os);
            RawFileData rf = new RawFileData((ByteArrayOutputStream) os);
            findProperty("excelFile").change(rf, context);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            e.printStackTrace();
        }
    }
}
