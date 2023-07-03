import lsfusion.base.file.RawFileData;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.xssf.usermodel.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;

public class XlsMergeCorrect  extends InternalAction {

    public XlsMergeCorrect(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

     protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        // при объединении не переносятся именованные ячейки - решаем тут

         RawFileData fOut = (RawFileData) getParam(0, context);  // файл экселя Итоговый
         RawFileData fIn  = (RawFileData) getParam(1, context);  // файл экселя Исходный
         //String  sheetNameOut = (String) getParam(1, context);   // имя таблицы
         //String  sheetNameIn = (String) getParam(3, context);    // имя таблицы
         try {
             XSSFWorkbook workbookIn  = new XSSFWorkbook(fIn.getInputStream());
             XSSFWorkbook workbookOut = new XSSFWorkbook(fOut.getInputStream());

             XSSFName nameOut;

             // Перенос именованных полей
             java.util.List<? extends Name> names = workbookIn.getAllNames();
             for (int i = 0; i < names.size(); i++) {
                 Name name = names.get(i);
                 nameOut = workbookOut.getAllNames().stream().filter(n -> n.getNameName().equals(name.getNameName())).findAny().orElse(null);
                 if (nameOut == null) {
                     nameOut = workbookOut.createName();
                     nameOut.setSheetIndex(name.getSheetIndex());
                     nameOut.setNameName(name.getNameName());
                 }
                 nameOut.setRefersToFormula(name.getRefersToFormula()); //.replace(sheetNameIn,sheetNameOut)
             }
             OutputStream os = new ByteArrayOutputStream();
            workbookOut.write(os);
            RawFileData rf = new RawFileData((ByteArrayOutputStream) os);
            findProperty("mergedExcel").change(rf, context);
          } catch (IOException e) {
            e.printStackTrace();
           } catch (ScriptingErrorLog.SemanticErrorException e) {
             e.printStackTrace();
        }
     }
}