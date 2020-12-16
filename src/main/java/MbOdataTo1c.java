import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import org.apache.commons.net.pop3.ExtendedPOP3Client;
import org.apache.olingo.client.api.Configuration;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetRequest;
//
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.ODataClientErrorException;
import org.apache.olingo.client.api.communication.ODataServerErrorException;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityCreateRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntityRequest;
import org.apache.olingo.client.api.communication.response.ODataResponse;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.*;
import org.apache.olingo.client.api.http.HttpClientException;

import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.client.core.domain.ClientCollectionValueImpl;
import org.apache.olingo.client.core.domain.ClientPrimitiveValueImpl;

import org.apache.olingo.client.core.http.BasicAuthHttpClientFactory;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.format.ContentType;

import java.net.URI;


public class MbOdataTo1c {
    static public final String STANDART_ODATA = "StandardODATA";
    String urlBase1c;
    String objectFullQualifiedName;
    ClientEntity root;
    final protected ODataClient client;

    public MbOdataTo1c(String objectFullQualifiedName_,String urlBase1c_,String userName, String password) {
        urlBase1c=urlBase1c_;
        client = ODataClientFactory.getClient();
        objectFullQualifiedName = objectFullQualifiedName_;
        Configuration configuration = client.getConfiguration();

        if (!userName.isEmpty()) {
            configuration.setHttpClientFactory(new BasicAuthHttpClientFactory(userName, password));
        }
        configuration.setDefaultPubFormat(ContentType.JSON);
        configuration.setDefaultBatchAcceptFormat(ContentType.JSON);
        configuration.setContinueOnError(true);
        configuration.setDefaultValueFormat(ContentType.JSON);

        root = client.getObjectFactory().
                newEntity(new FullQualifiedName(STANDART_ODATA, objectFullQualifiedName));
    }

    public String insertEntity(String userName, String password) {
        final URI uri1 = client
                .newURIBuilder(urlBase1c)
                .appendEntitySetSegment(objectFullQualifiedName).build();

        final ODataEntityCreateRequest<ClientEntity> req1 = client
                .getCUDRequestFactory()
                .getEntityCreateRequest(uri1, root);

        req1.setAccept(ContentType.APPLICATION_JSON.toContentTypeString());
        req1.setContentType(ContentType.APPLICATION_JSON.toContentTypeString());
        if (userName.isEmpty()) {
            req1.addCustomHeader("Authorization",password);
        }
        String retCode="";
        try {
            ODataResponse resp = req1.execute();
            retCode = Integer.toString(resp.getStatusCode());
        } catch (HttpClientException | ODataClientErrorException | ODataServerErrorException e) {
            retCode = e.getMessage();
        }
        return retCode;
    }

    public void addRootProperty(String prop, String value) {
        root.getProperties()
                .add(
                        client.getObjectFactory()
                                .newPrimitiveProperty(prop, client.getObjectFactory()
                                        .newPrimitiveValueBuilder()
                                        .buildString(value)));
    }

    public  ClientCollectionValue  newTable(String tableName) {
        ClientCollectionValue  table1c = client.getObjectFactory().newCollectionValue("table") ;
        root.getProperties().add(client.getObjectFactory().newCollectionProperty(tableName,table1c));
        return table1c;
    }

    public  ClientCollectionValue  newSubTable(ClientComplexValue rowTable, String tableName) {
        ClientCollectionValue  table1c = client.getObjectFactory().newCollectionValue("subtable") ;
        rowTable.add(client.getObjectFactory().newCollectionProperty(tableName,table1c));
        return table1c;
    }

    public  ClientComplexValue  newTableRow(ClientCollectionValue  table1c) {
        ClientComplexValue   rowTable1c = client.getObjectFactory().newComplexValue("rowTable");
        table1c.add(rowTable1c);
        return rowTable1c;
    }

    public void addRowProperty(ClientComplexValue rowTable1c,String prop, String value) {
        rowTable1c.add(client.getObjectFactory()
                .newPrimitiveProperty(prop,
                        client.getObjectFactory()
                                .newPrimitiveValueBuilder()
                                .buildString(value)));
    }

}

