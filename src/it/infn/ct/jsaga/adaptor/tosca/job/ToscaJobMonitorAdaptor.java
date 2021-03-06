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

import fr.in2p3.jsaga.adaptor.base.usage.U;
import fr.in2p3.jsaga.adaptor.base.usage.UAnd;
import fr.in2p3.jsaga.adaptor.base.usage.UOptional;
import fr.in2p3.jsaga.adaptor.base.usage.Usage;
import it.infn.ct.jsaga.adaptor.tosca.ToscaAdaptorCommon;

import fr.in2p3.jsaga.adaptor.job.control.manage.ListableJobAdaptor;
import fr.in2p3.jsaga.adaptor.job.monitor.JobInfoAdaptor;
import fr.in2p3.jsaga.adaptor.job.monitor.JobStatus;
import fr.in2p3.jsaga.adaptor.job.monitor.QueryIndividualJob;
import fr.in2p3.jsaga.adaptor.security.impl.UserPassSecurityCredential;
import fr.in2p3.jsaga.adaptor.ssh3.job.SSHJobMonitorAdaptor;
import static it.infn.ct.jsaga.adaptor.tosca.ToscaAdaptorCommon.LS;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.json.simple.parser.ParseException;

import org.ogf.saga.error.*;

/* *********************************************
 * *** Istituto Nazionale di Fisica Nucleare ***
 * ***      Sezione di Catania (Italy)       ***
 * ***        http://www.ct.infn.it/         ***
 * *********************************************
 * File:    rOCCIJobControlAdaptor.java
  * Authors: Giuseppe LA ROCCA, Riccardo BRUNO
 * Email:   <giuseppe.larocca, riccardo.bruno>@ct.infn.it
 * Ver.:    1.0.0
 * Date:    24 February 2016
 * *********************************************/

public class ToscaJobMonitorAdaptor extends ToscaAdaptorCommon 
                                      implements QueryIndividualJob, 
                                                 ListableJobAdaptor, 
                                                 JobInfoAdaptor
{      
  
  protected static final String ATTRIBUTES_TITLE = "attributes_title";
  protected static final String MIXIN_OS_TPL = "mixin_os_tpl";
  protected static final String MIXIN_RESOURCE_TPL = "mixin_resource_tpl";
  protected static final String PREFIX = "prefix";  
  
  private SSHJobMonitorAdaptor sshMonitorAdaptor = 
          new SSHJobMonitorAdaptor();
  
  private static final Logger log = 
          Logger.getLogger(ToscaJobMonitorAdaptor.class);
  
  @Override
  public void connect(String userInfo, String host, int port, 
                      String basePath, Map attributes) 
              throws NotImplementedException, 
                     AuthenticationFailedException, 
                     AuthorizationFailedException, 
                     IncorrectURLException, 
                     BadParameterException, 
                     TimeoutException, 
                     NoSuccessException 
  { 
    // Get reference to JobMonitorAdaptor for AdaptorCommon
    jobMonitor = this;

    super.connect(userInfo, host, port, basePath, attributes);    
  }
    
  @Override
  public String getType() {
    return "tosca";
  }
  
  @Override
  public JobStatus getStatus(String nativeJobId) 
                   throws TimeoutException, NoSuccessException 
  {  
    JobStatus result = null;    
    log.debug("getStatus (begin) '"+nativeJobId+"'");
    
    // Get and retrieve info from JobId
    String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
    String sshJobId    = jobIdInfo[1];
    tosca_UUID         = jobIdInfo[2];
    String sshPublicIP = jobIdInfo[3];
    int sshPort        = Integer.parseInt(jobIdInfo[4]);
    ssh_username       = jobIdInfo[5];
    ssh_password       = jobIdInfo[6];                                                          
            
    credential.setUsername(ssh_username);
    credential.setPassword(ssh_password);            
    sshMonitorAdaptor.setSecurityCredential(
            new UserPassSecurityCredential(ssh_username, ssh_password)
    );    
    try {
        sshMonitorAdaptor.connect(null, sshPublicIP, sshPort, null, new HashMap());
        result = sshMonitorAdaptor.getStatus(sshJobId);
    } catch (NotImplementedException ex) {
        java.util.logging.Logger.getLogger(ToscaJobMonitorAdaptor.class.getName()).log(Level.SEVERE, null, ex);
    } catch (AuthenticationFailedException ex) {
        java.util.logging.Logger.getLogger(ToscaJobMonitorAdaptor.class.getName()).log(Level.SEVERE, null, ex);
    } catch (AuthorizationFailedException ex) {
        java.util.logging.Logger.getLogger(ToscaJobMonitorAdaptor.class.getName()).log(Level.SEVERE, null, ex);
    } catch (BadParameterException ex) {
        java.util.logging.Logger.getLogger(ToscaJobMonitorAdaptor.class.getName()).log(Level.SEVERE, null, ex);
    }
    
    log.info("");
    log.info("getStatus() (end)");    
    
    return result;    
  }
  
  @Override
  public String[] list() throws PermissionDeniedException, TimeoutException, NoSuccessException 
  {
    return sshMonitorAdaptor.list();
  }       
  
  @Override
  public Date getCreated(String nativeJobId) 
              throws NotImplementedException, NoSuccessException 
  {    
    Date result = null;
    log.info("getCreated() (start)");
    
    // Get and retrieve info from JobId
    String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
    String sshJobId    = jobIdInfo[1];
    tosca_UUID         = jobIdInfo[2];
    String sshPublicIP = jobIdInfo[3];
    int sshPort        = Integer.parseInt(jobIdInfo[4]);
    ssh_username       = jobIdInfo[5];
    ssh_password       = jobIdInfo[6];                                                          
    
    result = sshMonitorAdaptor.getCreated(sshJobId);
    log.info("getCreated() (end)");    
    return result;
  }
  
  @Override
  public Date getStarted(String nativeJobId) 
              throws NotImplementedException, NoSuccessException 
  {     
    Date result = null;
    log.info("getStarted() (begin)");
    
    // Get and retrieve info from JobId
    String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
    String sshJobId    = jobIdInfo[1];
    tosca_UUID         = jobIdInfo[2];
    String sshPublicIP = jobIdInfo[3];
    int sshPort        = Integer.parseInt(jobIdInfo[4]);
    ssh_username       = jobIdInfo[5];
    ssh_password       = jobIdInfo[6];                                                          
    
    result = sshMonitorAdaptor.getStarted(sshJobId);
    log.info("getStarted() (end)");
    return result;
  }
  
  @Override
  public Date getFinished(String nativeJobId) 
              throws NotImplementedException, NoSuccessException 
  {    
    Date result = null;
    log.info("getFinished() (begin)");
    
    // Get and retrieve info from JobId
    String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
    String sshJobId    = jobIdInfo[1];
    tosca_UUID         = jobIdInfo[2];
    String sshPublicIP = jobIdInfo[3];
    int sshPort        = Integer.parseInt(jobIdInfo[4]);
    ssh_username       = jobIdInfo[5];
    ssh_password       = jobIdInfo[6];                                                          
    
    result = sshMonitorAdaptor.getFinished(sshJobId);
    log.info("getFinished() (end)");
    return result;
  }

  @Override
  public Integer getExitCode(String nativeJobId) 
                 throws NotImplementedException, NoSuccessException 
  {        
    Integer result = null;
    log.info("getExitCode() (begin)");
    
    // Get and retrieve info from JobId
    String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
    String sshJobId    = jobIdInfo[1];
    tosca_UUID         = jobIdInfo[2];
    String sshPublicIP = jobIdInfo[3];
    int sshPort        = Integer.parseInt(jobIdInfo[4]);
    ssh_username       = jobIdInfo[5];
    ssh_password       = jobIdInfo[6];                                                          
    
    result = sshMonitorAdaptor.getExitCode(sshJobId);
    log.info("getExitCode() (end)");
    return result;
  }
  
  @Override
  public String[] getExecutionHosts(String nativeJobId) 
                  throws NotImplementedException, NoSuccessException 
  {        
    String[] result = null;
    log.info("getExecutionHosts() (begin)");
    
    // Get and retrieve info from JobId
    String[] jobIdInfo = getInfoFromNativeJobId(nativeJobId);
    String sshJobId    = jobIdInfo[1];
    tosca_UUID         = jobIdInfo[2];
    String sshPublicIP = jobIdInfo[3];
    int sshPort        = Integer.parseInt(jobIdInfo[4]);
    ssh_username       = jobIdInfo[5];
    ssh_password       = jobIdInfo[6];                                                          
    
    result = sshMonitorAdaptor.getExecutionHosts(sshJobId);
    log.info("getExecutionHosts() (end)");
    return result;
  }
  
  @Override
  public Usage getUsage()
  {
    return new UAnd.Builder()
                    .and(super.getUsage())
                    .and(new U(ATTRIBUTES_TITLE))
                    .and(new U(MIXIN_OS_TPL))
                    .and(new U(MIXIN_RESOURCE_TPL))
                    .and(new UOptional(PREFIX))
                    .build();
  }
}