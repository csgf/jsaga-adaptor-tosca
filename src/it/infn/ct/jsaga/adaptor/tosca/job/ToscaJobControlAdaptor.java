/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package it.infn.ct.jsaga.adaptor.tosca.job;

import it.infn.ct.jsaga.adaptor.tosca.ToscaAdaptorCommon;
import fr.in2p3.jsaga.adaptor.base.usage.U;
import fr.in2p3.jsaga.adaptor.base.usage.UAnd;
import fr.in2p3.jsaga.adaptor.base.usage.Usage;
import fr.in2p3.jsaga.adaptor.job.control.advanced.CleanableJobAdaptor;
import fr.in2p3.jsaga.adaptor.job.control.staging.StagingJobAdaptorTwoPhase;
import fr.in2p3.jsaga.adaptor.job.control.staging.StagingTransfer;
import fr.in2p3.jsaga.adaptor.job.control.JobControlAdaptor;
import fr.in2p3.jsaga.adaptor.job.monitor.JobMonitorAdaptor;
import fr.in2p3.jsaga.adaptor.job.BadResource;
import fr.in2p3.jsaga.adaptor.job.control.description.JobDescriptionTranslator;
import fr.in2p3.jsaga.adaptor.security.impl.UserPassSecurityCredential;
import fr.in2p3.jsaga.adaptor.ssh3.job.SSHJobControlAdaptor;
import org.ogf.saga.error.NoSuccessException;
import org.ogf.saga.error.NotImplementedException;
import org.ogf.saga.error.AuthenticationFailedException;
import org.ogf.saga.error.AuthorizationFailedException;
import org.ogf.saga.error.BadParameterException;
import org.ogf.saga.error.IncorrectURLException;
import org.ogf.saga.error.TimeoutException;
import org.ogf.saga.error.PermissionDeniedException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import org.json.simple.parser.ParseException;

/* *********************************************
 * *** Istituto Nazionale di Fisica Nucleare ***
 * ***      Sezione di Catania (Italy)       ***
 * ***        http://www.ct.infn.it/         ***
 * *********************************************
 * File:    ToscaJobControlAdaptor.java
 * Authors: Giuseppe LA ROCCA, 
 *          Riccardo BRUNO,
 *          Marco FARGETTA
 * Email:   <giuseppe.larocca,
 *           riccardo.bruno,
 *           marco.fargetta>@ct.infn.it
 * Ver.:    1.0.0
 * Date:    17 March 2016
 * *********************************************/
public class ToscaJobControlAdaptor extends ToscaAdaptorCommon
        implements JobControlAdaptor,
        StagingJobAdaptorTwoPhase,
        CleanableJobAdaptor {
    
    private static final Logger log
            = Logger.getLogger(ToscaJobControlAdaptor.class);

    private ToscaJobMonitorAdaptor toscaJobMonitorAdaptor
            = new ToscaJobMonitorAdaptor();

    private SSHJobControlAdaptor sshControlAdaptor
            = new SSHJobControlAdaptor();

    protected String tosca_id = null;
    private String action = "";
    private String tosca_template = "";  
    private String wait_ms = "";
    private String max_waits = "";
    int wait_ms_val = 30000;         // wait_ms value
    int max_waits_val = 20;       // max_waits value
    
    @Override
    public void connect(String userInfo, String host, int port, String basePath, Map attributes)
            throws NotImplementedException,
            AuthenticationFailedException,
            AuthorizationFailedException,
            IncorrectURLException,
            BadParameterException,
            TimeoutException,
            NoSuccessException {

        log.debug("Connect (begin)");
        
        // Get reference to JobControlAdaptor for AdaptorCommon
        jobControl = this;

        // Get endpoint parameters
        tosca_template = (String) attributes.get(TOSCA_TEMPLATE);        
        wait_ms = (String) attributes.get(TOSCA_WAITMS);
        if(null != wait_ms && wait_ms.length() > 0) 
            try {
                wait_ms_val = Integer.parseInt(wait_ms);
            } catch(NumberFormatException nfe) {
                log.warn("Invalid wait_ms value: '"+wait_ms+"'");
            }
        max_waits = (String) attributes.get(TOSCA_MAXWAITS);                        
        if(null != max_waits && max_waits.length() > 0) 
            try {
                max_waits_val = Integer.parseInt(max_waits);
            } catch(NumberFormatException nfe) {
                log.warn("Invalid wait_ms value: '"+max_waits+"'");
            }

        // View parameters
        log.debug("userInfo      : '" + userInfo + "'" + LS
                + "host          : '" + host + "'" + LS
                + "port          : '" + port + "'" + LS
                + "basePath      : '" + basePath + "'" + LS
                + "attributes    : '" + attributes + "'" + LS
                + "action        : '" + action + "'" + LS
                + "tosca_template: '" + tosca_template + "'" +LS
                + "wait_ms       : '" + wait_ms + "'" +LS
                + "max_waits     : '" + max_waits + "'" 
        );

        try {
            endpoint = new URL("http", host, port, basePath);
        } catch (MalformedURLException ex) {
            log.error("Error in the service end-point creation" + ex);
            throw new BadParameterException(ex);
        }
        log.debug("action:" + action);
        log.debug("tosca_template: " + tosca_template);
    }

    @Override
    public void start(String nativeJobId) throws PermissionDeniedException,
            TimeoutException,
            NoSuccessException {

        log.debug("start (begin)");
        
        // Get and retrieve info from JobId
        String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
        String sshJobId    = jobIdInfo[1];
        tosca_UUID         = jobIdInfo[2];
        String sshPublicIP = jobIdInfo[3];
        int sshPort        = Integer.parseInt(jobIdInfo[4]);
        ssh_username       = jobIdInfo[5];
        ssh_password       = jobIdInfo[6]; 
        
        try {
            sshControlAdaptor.setSecurityCredential(
                    new UserPassSecurityCredential(ssh_username, ssh_password)
            );
            sshControlAdaptor.connect(null, sshPublicIP, sshPort, null, new HashMap());
            sshControlAdaptor.start(sshJobId);
        } catch (NotImplementedException ex) {
            throw new NoSuccessException(ex);
        } catch (AuthenticationFailedException ex) {
            throw new PermissionDeniedException(ex);
        } catch (AuthorizationFailedException ex) {
            throw new PermissionDeniedException(ex);
        } catch (BadParameterException ex) {
            throw new NoSuccessException(ex);
        }
        log.debug("start (end)");
    }

    @Override
    public void cancel(String nativeJobId) throws PermissionDeniedException,
            TimeoutException,
            NoSuccessException {
        log.debug("cancel (begin)");
        
        // Get and retrieve info from JobId
        String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
        String sshJobId    = jobIdInfo[1];
        tosca_UUID         = jobIdInfo[2];
        String sshPublicIP = jobIdInfo[3];
        int sshPort        = Integer.parseInt(jobIdInfo[4]);
        ssh_username       = jobIdInfo[5];
        ssh_password       = jobIdInfo[6];                                               
        
        try {
            sshControlAdaptor.setSecurityCredential(
                    new UserPassSecurityCredential(ssh_username, ssh_password)
            );
            sshControlAdaptor.connect(null, sshPublicIP, sshPort, null, new HashMap());
            sshControlAdaptor.cancel(sshJobId);
        } catch (NotImplementedException ex) {
            throw new NoSuccessException(ex);
        } catch (AuthenticationFailedException ex) {
            throw new PermissionDeniedException(ex);
        } catch (AuthorizationFailedException ex) {
            throw new PermissionDeniedException(ex);
        } catch (BadParameterException ex) {
            throw new NoSuccessException(ex);
        } finally {
            this.releaseToscaResources();
        }
        log.debug("cancel (end)");
    }        

    @Override
    public void clean(String nativeJobId) throws PermissionDeniedException,
            TimeoutException,
            NoSuccessException {                
        log.debug("clean (begin)");
        
        // Get and retrieve info from JobId
        String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
        String sshJobId    = jobIdInfo[1];
        tosca_UUID         = jobIdInfo[2];
        String sshPublicIP = jobIdInfo[3];
        int sshPort        = Integer.parseInt(jobIdInfo[4]);
        ssh_username       = jobIdInfo[5];
        ssh_password       = jobIdInfo[6];                        
        
        try {
            sshControlAdaptor.setSecurityCredential(
                    new UserPassSecurityCredential(ssh_username, ssh_password)
            );
            sshControlAdaptor.connect(null, sshPublicIP, sshPort, null, new HashMap());
            sshControlAdaptor.clean(sshJobId);

            // Releasing TOSCA resources
            releaseToscaResources();
        } catch (NotImplementedException ex) {
            throw new NoSuccessException(ex);
        } catch (AuthenticationFailedException ex) {
            throw new PermissionDeniedException(ex);
        } catch (AuthorizationFailedException ex) {
            throw new PermissionDeniedException(ex);
        } catch (BadParameterException ex) {
            throw new NoSuccessException(ex);
        }
        log.debug("clean (end)");
    }                            
    
    private String submitTosca() 
        throws IOException,
               ParseException,
               BadResource,
               NoSuccessException {   
        StringBuilder orchestrator_result=new StringBuilder("");
        StringBuilder postData = new StringBuilder();
        postData.append("{ \"template\": \"");
        String tosca_template_content="";
        try {
            tosca_template_content = new String(Files.readAllBytes(Paths.get(tosca_template))).replace("\n", "\\n"); 
            postData.append(tosca_template_content);
        } catch (IOException ex) {
            log.error("Template '"+tosca_template+"'is not readable");
            throw new BadResource("Template '"+tosca_template+"'is not readable; template:" +LS
                                 +"'"+tosca_template_content+"'"
            );
        }
        postData.append("\"  }");

        log.debug("JSON Data sent to the orchestrator: \n" + postData);
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) endpoint.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            conn.setDoOutput(true);
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(postData.toString());
            wr.flush();
            wr.close();
            log.debug("Orchestrator status code: " + conn.getResponseCode());
            log.debug("Orchestrator status message: " + conn.getResponseMessage());
            if (conn.getResponseCode() == 201) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                orchestrator_result = new StringBuilder();
                String ln;
                while ((ln = br.readLine()) != null) {
                    orchestrator_result.append(ln);
                }
                
                log.debug("Orchestrator result: " + orchestrator_result);
                String orchestratorDoc = orchestrator_result.toString();
                tosca_UUID = getDocumentValue(orchestratorDoc,"uuid");
                log.debug("Created resource has UUID: '"+tosca_UUID+"'");
                return orchestratorDoc;
                
            }
        } catch (IOException ex) {
            log.error("Connection error with the service at " + endpoint.toString());
            log.error(ex);
            throw new NoSuccessException("Connection error with the service at " + endpoint.toString());
        } catch (ParseException ex) {
            log.error("Orchestrator response not parsable");
            throw new NoSuccessException("Orchestrator response not parsable:"+LS
                                        +"'"+orchestrator_result.toString()+"'");
        }
        return tosca_UUID;
    }
    
    private String waitToscaResource() 
            throws NoSuccessException,
                   BadResource,
                   TimeoutException {
         int attempts = 0;
         int max_attempts = max_waits_val;
         int wait_step= wait_ms_val;
         String toscaStatus = "CREATE_IN_PROGRESS";
         String toscaDeployment="";
         
         for(attempts=0;
             tosca_UUID != null 
          && attempts < max_attempts 
          && toscaStatus.equals("CREATE_IN_PROGRESS");
             attempts++) {
             try {
                 log.debug("Waiting ("+wait_step+"ms) for resource creation; attempt: "+(attempts+1)+"/"+max_attempts+" ...");
                 Thread.sleep(wait_step);
             } catch (InterruptedException e1) {
                 // TODO Auto-generated catch block
                 e1.printStackTrace();
             }
             toscaDeployment = getToscaDeployment(tosca_UUID);
             try {
                 log.debug("Deployment: " + toscaDeployment);
                 toscaStatus = getDocumentValue(toscaDeployment, "status");
                 log.debug("Deployment " + tosca_UUID + " has status '" + toscaStatus + "'");
             } catch (ParseException ex) {
                 log.warn("Impossible to parse the tosca deployment json: '"+toscaDeployment+"'");
             }
         }
         if(!toscaStatus.equals("CREATE_COMPLETE")) {
             log.debug("Deployments error for "+ tosca_UUID+" with status '"+toscaStatus+"'. Attempts "+attempts+"/" + max_attempts);
             if(attempts >= max_attempts)
                 throw new TimeoutException("Reached timeout while waiting for resource");
             else
                throw new NoSuccessException("Deployment error.");
         }
         
         return toscaDeployment;
    }
        
    /**
     * Free all allocated resources
     */
    protected void releaseToscaResources() {
        if(tosca_UUID != null) {
            log.debug("Releasing Tosca resource '"+tosca_UUID+"'");
            // Release of Tosca resource not yet implemented            
            if(null!=tosca_UUID && tosca_UUID.length()>0)
                deleteToscaDeployment(tosca_UUID);
            else log.warn("Called delete on NULL or empty UUID");
        }        
    }
        
    @Override
    public String submit(String jobDesc, boolean checkMatch, String uniqId)
            throws PermissionDeniedException,
            TimeoutException,
            NoSuccessException,
            BadResource {
        
        log.debug("submit (begin)");
        log.debug("action:" + action);
        log.debug("jobDesc:" + jobDesc);
        log.debug("checkMatch:" + checkMatch);
        log.debug("uniqId:" + uniqId);
        String result = "";
        String ssh_publicIP = "";
        int ssh_port = 22;
              
        // SUbmit works in two stages; first create the Tosca resource
        // from the given toca_template, then submit the job to an 
        // SSH instance belonging to the Tosca resources
        try {
            log.info("Creating a new tosca resource, please wait ...");            
            log.debug("tosca_template: '" + tosca_template + "'");
            
            // Create Tosca resource form tosca_template, then wait
            // for its creation and determine an access point with SSH:
            // IP/Port and credentials (username, PublicKey and PrivateKey)
            String doc = submitTosca();

            // Now waits until the resource is available
            // A maximum number of attempts will be done
            // until the resource will be made available
            doc = waitToscaResource();

            // Once tosca resource is ready, submit to SSH
            // String ssh_publicIP = ToscaResults[0];
            // String ssh_port = toscaResults[1];
            String[] sshCredentials = getToscaResourceCredentials(doc);
            ssh_publicIP = sshCredentials[0];
            ssh_port     = Integer.parseInt(sshCredentials[1]);
            ssh_username = sshCredentials[2];
            ssh_password = sshCredentials[3];
            
            log.debug(LS+"IP      : '"+ssh_publicIP+"'"+
                      LS+"Port    : '"+ssh_port    +"'"+
                      LS+"username: '"+ssh_username+"'"+
                      LS+"password: '"+ssh_password+"'"
                     );           
            
          //ssh_username="root";
          //ssh_password="IvYAGaRc";
            
            credential.setUsername(ssh_username);
            credential.setPassword(ssh_password);            
            sshControlAdaptor.setSecurityCredential(
                    new UserPassSecurityCredential(ssh_username, ssh_password)
            );
            sshControlAdaptor.connect(null, ssh_publicIP, ssh_port, null, new HashMap());
        } catch (NotImplementedException ex) {
            releaseToscaResources();
            throw new NoSuccessException(ex);
        } catch (AuthenticationFailedException ex) {
            releaseToscaResources();
            throw new PermissionDeniedException(ex);
        } catch (AuthorizationFailedException ex) {
            releaseToscaResources();
            throw new PermissionDeniedException(ex);
        } catch (BadParameterException ex) {
            releaseToscaResources();
            throw new NoSuccessException(ex);
        } catch (Exception ex) {
            releaseToscaResources();
            throw new NoSuccessException(ex);
        }
        //result = sshControlAdaptor.submit(jobDesc, checkMatch, uniqId)
        //        + "@" + ssh_publicIP + ":" + ssh_port + "#" + tosca_UUID;
        result = sshControlAdaptor.submit(jobDesc, checkMatch, uniqId) + "#" + tosca_UUID;                        
        
        log.debug("submit (end)");
        log.debug("JobId: '"+result+"'");
        this.tosca_id = result;
        return result;
    }
   
    @Override
    public StagingTransfer[] getInputStagingTransfer(String nativeJobId)
            throws PermissionDeniedException,
            TimeoutException,
            NoSuccessException {
        StagingTransfer[] result = null;
        log.debug("getInputStagingTransfer (begin)");
        
        // Get and retrieve info from JobId
        String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
        String sshJobId    = jobIdInfo[1];
        tosca_UUID         = jobIdInfo[2];
        String sshPublicIP = jobIdInfo[3];
        int sshPort        = Integer.parseInt(jobIdInfo[4]);
        ssh_username       = jobIdInfo[5];
        ssh_password       = jobIdInfo[6];                        
        
        try {                        
            sshControlAdaptor.setSecurityCredential(
                    new UserPassSecurityCredential(ssh_username, ssh_password)
            );
            sshControlAdaptor.connect(null, sshPublicIP, sshPort, null, new HashMap());
            result = sshControlAdaptor.getInputStagingTransfer(sshJobId);
            for(int i=0; i<result.length; i++)
                log.debug("result("+i+"): '" + result[i]+"'");
        } catch (NotImplementedException ex) {
            releaseToscaResources();
            throw new NoSuccessException(ex);
        } catch (AuthenticationFailedException ex) {
            releaseToscaResources();
            throw new PermissionDeniedException(ex);
        } catch (AuthorizationFailedException ex) {
            releaseToscaResources();
            throw new PermissionDeniedException(ex);
        } catch (BadParameterException ex) {
            releaseToscaResources();
            throw new NoSuccessException(ex);
        }
        // View result
        for (StagingTransfer tr : result) {
            log.debug("From: '" + tr.getFrom() + "' to '" + tr.getTo() + "'");
        }
        log.debug("getInputStagingTransfer (end)");
        return sftp2tosca(result);
    }

    @Override
    public StagingTransfer[] getOutputStagingTransfer(String nativeJobId)
            throws PermissionDeniedException,
            TimeoutException,
            NoSuccessException {
        StagingTransfer[] result = null;
        log.debug("getOutputStagingTransfer (begin)");

        // Get and retrieve info from JobId
        String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
        String sshJobId    = jobIdInfo[1];
        tosca_UUID         = jobIdInfo[2];
        String sshPublicIP = jobIdInfo[3];
        int sshPort        = Integer.parseInt(jobIdInfo[4]);
        ssh_username       = jobIdInfo[5];
        ssh_password       = jobIdInfo[6];                        
        
        try {
            log.debug("ssh_username: '"+ssh_username+"'");
            log.debug("ssh_password: '"+ssh_password+"'");
            sshControlAdaptor.setSecurityCredential(
                    new UserPassSecurityCredential(ssh_username, ssh_password)
            );
            sshControlAdaptor.connect(null, sshPublicIP, sshPort, null, new HashMap());
            result = sshControlAdaptor.getOutputStagingTransfer(sshJobId);
            for(int i=0; i<result.length; i++)
                log.debug("result("+i+"): '" + result[i]+"'");
        } catch (NotImplementedException ex) {
            releaseToscaResources();
            throw new NoSuccessException(ex);
        } catch (AuthenticationFailedException ex) {
            releaseToscaResources();
            throw new PermissionDeniedException(ex);
        } catch (AuthorizationFailedException ex) {
            releaseToscaResources();
            throw new PermissionDeniedException(ex);
        } catch (BadParameterException ex) {
            releaseToscaResources();
            throw new NoSuccessException(ex);
        }
        // View result
        for (StagingTransfer tr : result) {
            log.debug("From: '" + tr.getFrom() + "' to '" + tr.getTo() + "'");
        }
        log.debug("getOutputStagingTransfer (end)");
        return sftp2tosca(result);
    }

    private StagingTransfer[] sftp2tosca(StagingTransfer[] transfers) {
        int index = 0;
        StagingTransfer[] newTransfers = new StagingTransfer[transfers.length];

        log.debug("sftp2tosca");
        for (StagingTransfer tr : transfers) {
            log.debug("From: " + tr.getFrom() + " to " + tr.getTo());
            StagingTransfer newTr
                    = new StagingTransfer(
                            tr.getFrom().replace("sftp://", "tosca://"),
                            tr.getTo().replace("sftp://", "tosca://"),
                            tr.isAppend());

            newTransfers[index++] = newTr;
            log.debug("From: " + newTr.getFrom() + " to " + newTr.getTo());
        }
        return newTransfers;
    }

    @Override
    public String getStagingDirectory(String nativeJobId)
            throws PermissionDeniedException,
            TimeoutException,
            NoSuccessException {
        String result = "";
        log.debug("getStagingDirectory (begin)");                        
                
        // Get and retrieve info from JobId
        String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
        String sshJobId    = jobIdInfo[1];
        tosca_UUID         = jobIdInfo[2];
        String sshPublicIP = jobIdInfo[3];
        int sshPort        = Integer.parseInt(jobIdInfo[4]);
        ssh_username       = jobIdInfo[5];
        ssh_password       = jobIdInfo[6]; 
        
        try {
            sshControlAdaptor.setSecurityCredential(
                    new UserPassSecurityCredential(ssh_username, ssh_password)
            );
            sshControlAdaptor.connect(null, sshPublicIP, sshPort, null, new HashMap());
            result = sshControlAdaptor.getStagingDirectory(sshJobId);
            log.debug("result: " + result);
        } catch (NotImplementedException ex) {
            throw new NoSuccessException(ex);
        } catch (AuthenticationFailedException ex) {
            throw new PermissionDeniedException(ex);
        } catch (AuthorizationFailedException ex) {
            throw new PermissionDeniedException(ex);
        } catch (BadParameterException ex) {
            throw new NoSuccessException(ex);
        }
        log.debug("getStagingDirectory (end)");
        return result;
    }

    @Override
    public JobMonitorAdaptor getDefaultJobMonitor() {
        return toscaJobMonitorAdaptor;
    }

    @Override
    public JobDescriptionTranslator getJobDescriptionTranslator()
            throws NoSuccessException {
        return sshControlAdaptor.getJobDescriptionTranslator();
    }

    @Override
    public Usage getUsage() {
        return new UAnd.Builder()
                .and(super.getUsage())
                .and(new U(USER_NAME))
                .and(new U(TOKEN))
                .build();
    }
}
