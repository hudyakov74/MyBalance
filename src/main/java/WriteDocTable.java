import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import org.apache.commons.io.IOUtils;
import org.apache.olingo.client.api.Configuration;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.domain.ClientCollectionValue;
import org.apache.olingo.client.api.domain.ClientComplexValue;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.http.BasicAuthHttpClientFactory;
import org.apache.olingo.commons.api.format.ContentType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

//import  ;

public class WriteDocTable extends InternalAction {

//    Map<LocalDateTime, Map<String, Object>> data;

    static public final String STANDART_ODATA = "StandardODATA";
    final protected ODataClient client;
    String odataUrl;
    String login;
    String password;
    LocalDate  dateDoc;

    public WriteDocTable(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
        client = ODataClientFactory.getClient();
        Configuration configuration = client.getConfiguration();
        configuration.setDefaultPubFormat(ContentType.JSON);
        configuration.setDefaultBatchAcceptFormat(ContentType.JSON);
        configuration.setContinueOnError(true);
        configuration.setDefaultValueFormat(ContentType.JSON);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        String st = "";
       lsfusion.base.file.RawFileData f = (lsfusion.base.file.RawFileData) getParam(0, context);
        odataUrl = (String) getParam(1, context);
        login = (String) getParam(2, context);
        password = (String) getParam(3, context);
        dateDoc = (LocalDate)getParam(4, context);
        client.getConfiguration().setHttpClientFactory(
                new BasicAuthHttpClientFactory(login, password)
        );

        try {
            st = IOUtils.toString(f.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedReader reader = new BufferedReader(new StringReader(st));


       insertTest(reader);
    }

    public String insertTest(BufferedReader reader) {
       MbOdataTo1c o = new MbOdataTo1c("Document_ПоступлениеТоваровУслуг",
                odataUrl, login, password);
        o.addRootProperty("Date", dateDoc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00" ); //+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        ClientCollectionValue t = o.newTable("Товары");

        Integer  y = 0;
        String line = "";
        while (true) {
            try {
                if (!((line = reader.readLine()) != null)) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            String[] values = line.split(";");
            ClientComplexValue row = o.newTableRow(t);
            o.addRowProperty(row, "LineNumber",Integer.toString(y+=1));
            o.addRowProperty(row, "Количество",values[0]);
            o.addRowProperty(row, "Цена",values[1]);
            o.addRowProperty(row, "Сумма",values[2]);
            o.addRowProperty(row, "Коэффициент","1");
            o.addRowProperty(row, "Номенклатура_Key", values[3]);
            o.addRowProperty(row, "ЕдиницаИзмерения_Key",values[4]);
        }
        String res="";
         res = o.insertEntity(login, password);
        return res;
    }
}
