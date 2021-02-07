        import lsfusion.base.file.RawFileData;
        import lsfusion.server.data.sql.exception.SQLHandledException;
        import lsfusion.server.language.ScriptingErrorLog;
        import lsfusion.server.language.ScriptingLogicsModule;
        import lsfusion.server.logics.action.controller.context.ExecutionContext;
        import lsfusion.server.logics.classes.ValueClass;
        import lsfusion.server.logics.property.classes.ClassPropertyInterface;
        import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
        import org.apache.poi.ss.usermodel.CellType;
        import org.apache.poi.xssf.usermodel.XSSFRow;
        import org.apache.poi.xssf.usermodel.XSSFSheet;
        import org.apache.poi.xssf.usermodel.XSSFWorkbook;
        import java.io.ByteArrayOutputStream;
        import java.io.IOException;
        import java.io.OutputStream;
        import java.sql.SQLException;
        import java.util.HashMap;
        import java.util.Map;

        public class XlsCreateRowOutline  extends InternalAction {
    public XlsCreateRowOutline(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }
    XSSFSheet sheet;

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        RawFileData f =  (RawFileData)getParam(0, context);
        Map<Integer,Map<Integer,Integer>> ol = new HashMap<>();
        for (int i =0;i<20;i++)  ol.put(i,new HashMap<>());
        int currentLevel=0;
        int rowLevel=0;
        int rowIndex=0;
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(f.getInputStream() );
            sheet = workbook.getSheetAt(0);
            for (  rowIndex=0; sheet.getLastRowNum()>rowIndex;rowIndex++) {
                XSSFRow removingRow=sheet.getRow(rowIndex);
                if(removingRow!=null){
                    if (
                            removingRow.getCell(1).getCellType() == CellType.NUMERIC
                            &&  removingRow.getCell(1).getNumericCellValue()>=0
                            &&  removingRow.getCell(1).getNumericCellValue()<7
                    ) {
                        rowLevel = (int)removingRow.getCell(1).getNumericCellValue();
                       if (currentLevel<rowLevel) {
                           // уровень повышен
                           ol.get(currentLevel).put(0,1);
                           ol.get(currentLevel).put(1,rowIndex);
                           currentLevel=rowLevel;
                       }
                           // уровень понижен - сброс уровня
                   while (currentLevel>rowLevel) {
                       currentLevel--;
                       if  (ol.get(currentLevel).containsKey(0) && (ol.get(currentLevel).get(0)==1)) {
                           ol.get(currentLevel).put(0, 0);
                           sheet.groupRow(ol.get(currentLevel).get(1), rowIndex - 1);
                          }
                   }
                     }
                }
            }
            rowLevel = 0;
        // уровень понижен - сброс уровня
        while (currentLevel>rowLevel) {
            currentLevel--;
            if  (ol.get(currentLevel).containsKey(0) && (ol.get(currentLevel).get(0)==1)) {
                ol.get(currentLevel).put(0, 0);
                sheet.groupRow(ol.get(currentLevel).get(1), rowIndex - 1);
            }
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

