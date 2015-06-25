package com.hp.demo.ali.agm;

import com.hp.demo.ali.rest.AgmRestService;
import com.jayway.jsonpath.JsonPath;
import org.apache.log4j.Logger;
import org.hp.almjclient.connection.ServiceResourceAdapter;
import org.hp.almjclient.exceptions.ALMRestException;
import org.hp.almjclient.exceptions.RestClientException;
import org.hp.almjclient.model.marshallers.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by panuska on 19.1.15.
 */
public class ScmRepositoryHandler extends UnifyOtherEntityHandler {
    private static Logger log = Logger.getLogger(ScmRepositoryHandler.class.getName());

    public ScmRepositoryHandler(String sharedParameter) {
        super(sharedParameter);
    }

    @Override
    public Entity row(Entity entity) {
        Entity result = super.row(entity);
        try {
            int id = result.getId();
            String workspaceId = entity.getFieldValue("product-group-id").getValue();
            String username = entity.getFieldValue("username").getValue();
            String password = entity.getFieldValue("password").getValue();
            ServiceResourceAdapter adapter = AgmRestService.getAdapter();

            Map<String, String> headers = new HashMap<>(1);
            headers.put("INTERNAL_DATA", "20150313");
            headers.put("Accept", "application/json");
            headers.put("Content-Type", "application/json");
            adapter.addSessionCookie("AGM_STATE="+"20150313");

            String json;
            int credentialsId;
            String credentialsUrl = AgmRestService.getCollectionBaseUrl()+"workspace/"+workspaceId+"/ali/credentials/scm-repository/"+id;
            log.debug("Getting credentials from "+credentialsUrl);
            try {
                json = adapter.getWithHeaders(String.class, credentialsUrl, headers, ServiceResourceAdapter.ContentType.JSON);
                log.debug("Credentials are: "+json);
                credentialsId = JsonPath.read(json, "$.id");
            } catch (ALMRestException e) {
                json = "{\"values\":{\"USERNAME\":\""+username+"\", \"PASSWORD\":\""+password+"\"}}";
                log.debug("No credentials found; creating new credentials: "+json);
                adapter.postWithHeaders(String.class, credentialsUrl, json, headers, ServiceResourceAdapter.ContentType.JSON);
                return result;
            }
            json = "{\"id\":"+credentialsId+", \"values\":{\"USERNAME\":\""+username+"\", \"PASSWORD\":\""+password+"\"}}";
            log.debug("Credentials found; updating credentials: "+json);
            adapter.putWithHeaders(String.class, credentialsUrl, json, headers, ServiceResourceAdapter.ContentType.JSON);
            return result;

        } catch (ALMRestException | RestClientException e) {
            throw new IllegalStateException("Cannot set scm-repository credentials", e);
        }
    }
}
