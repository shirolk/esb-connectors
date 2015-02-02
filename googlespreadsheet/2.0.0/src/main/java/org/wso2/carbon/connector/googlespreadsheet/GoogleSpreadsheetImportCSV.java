/*
*  Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.connector.googlespreadsheet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.ConnectException;

import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;

public class GoogleSpreadsheetImportCSV extends AbstractConnector {

	public static final String WORKSHEET_NAME = "worksheetName";
	public static final String SPREADSHEET_NAME = "spreadsheetName";
	public static final String FILE_PATH = "filePath";
	public static final String BATCH_ENABLE = "batchEnable";
	public static final String BATCH_SIZE = "batchSize";
    public static final String APPEND = "append";
	
	private static Log log = LogFactory
			.getLog(GoogleSpreadsheetImportCSV.class);

	public void connect(MessageContext messageContext) throws ConnectException {
		try {
			String worksheetName = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, WORKSHEET_NAME);
			String spreadsheetName = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, SPREADSHEET_NAME);
			String filePath = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, FILE_PATH);
			String batchEnable = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, BATCH_ENABLE);
			String batchSize = GoogleSpreadsheetUtils
					.lookupFunctionParam(messageContext, BATCH_SIZE);
            String append = GoogleSpreadsheetUtils.lookupFunctionParam(messageContext, APPEND);
            int lastRowId = 1;
			
			if (worksheetName == null || "".equals(worksheetName.trim())
					|| spreadsheetName == null
					|| "".equals(spreadsheetName.trim())) {
				log.error("Please make sure you have given a valid input for the worksheet, spreadsheet and csv name");
                ConnectException connectException = new ConnectException("Please make sure you have given a valid input for the worksheet, spreadsheet and csv name");
                GoogleSpreadsheetUtils.storeErrorResponseStatus(messageContext, connectException);
                return;
			}

			SpreadsheetService ssService = new GoogleSpreadsheetClientLoader(
					messageContext).loadSpreadsheetService();

			GoogleSpreadsheet gss = new GoogleSpreadsheet(ssService);

			SpreadsheetEntry ssEntry = gss
					.getSpreadSheetsByTitle(spreadsheetName);

            if(ssEntry != null) {

			GoogleSpreadsheetWorksheet gssWorksheet = new GoogleSpreadsheetWorksheet(
					ssService, ssEntry.getWorksheetFeedUrl());

			WorksheetEntry wsEntry = gssWorksheet
					.getWorksheetByTitle(worksheetName);

            if(wsEntry != null) {

			GoogleSpreadsheetCellData gssData = new GoogleSpreadsheetCellData(
					ssService);

            if((append != null) && append.equalsIgnoreCase("true"))
            {
                // Fetch the list feed of the worksheet.
            URL listFeedUrl = wsEntry.getListFeedUrl();
                ListFeed listFeed = ssService.getFeed(listFeedUrl, ListFeed.class);
                // Iterate through each row, get the last row id.
                for (ListEntry row : listFeed.getEntries()) {
                    lastRowId++;
                }
                // Increment the lastRowId to insert new data
                if(lastRowId >1)    {
                lastRowId++;
                }
            }

			
			if((batchEnable != null) && batchEnable.equalsIgnoreCase("true")) {
				
				 // Build list of cell addresses to be filled in
			    List<GoogleSpreadsheetCellAddress> cellAddrs = new ArrayList<GoogleSpreadsheetCellAddress>();
			    GoogleSpreadsheetBatchUpdater gssBatchUpdater = new GoogleSpreadsheetBatchUpdater(ssService);
			    int batchSizeInt = 0;
			    if(batchSize != null) {
			    	try {
			    		 batchSizeInt = Integer.parseInt(batchSize);
			    	} catch (NumberFormatException ex) {
			    		log.error("Please enter valid number for batch size");
                        GoogleSpreadsheetUtils.storeErrorResponseStatus(messageContext, ex);
                        return;
			    	}
			    }
			   
			    
			    if (filePath == null) {

					if (messageContext.getEnvelope().getBody().getFirstElement() != null) {
						String data = messageContext.getEnvelope().getBody()
								.getFirstElement().getText();
						// convert String into InputStream
						InputStream is = new ByteArrayInputStream(data.getBytes());

						// read it with BufferedReader
						BufferedReader br = new BufferedReader(
								new InputStreamReader(is));

						String line;
						int rowNumber = lastRowId;
						if(batchSizeInt > 0) {
						while ((line = br.readLine()) != null) {
							String[] recordList = line.split(",");
							for (int i = 1; i <= recordList.length; i++) {								
								cellAddrs.add(new GoogleSpreadsheetCellAddress(rowNumber, i, recordList[i - 1]));
							}
							if((rowNumber%batchSizeInt) == 0) {
								gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
								cellAddrs.clear();
							}
							rowNumber++;
						}
						if((rowNumber%batchSizeInt) != 0) {
							gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
						}
						br.close();
						} else {
							while ((line = br.readLine()) != null) {
								String[] recordList = line.split(",");
								for (int i = 1; i <= recordList.length; i++) {								
									cellAddrs.add(new GoogleSpreadsheetCellAddress(rowNumber, i, recordList[i - 1]));
								}								
								rowNumber++;
							}
							br.close();
							gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
						}
						
					}

				} else {

					BufferedReader br = new BufferedReader(new FileReader(filePath));
					String line;
					int rowNumber = lastRowId;
					if(batchSizeInt > 0) {
					while ((line = br.readLine()) != null) {
						String[] recordList = line.split(",");
						for (int i = 1; i <= recordList.length; i++) {
							cellAddrs.add(new GoogleSpreadsheetCellAddress(rowNumber, i, recordList[i - 1]));
						}
						if((rowNumber%batchSizeInt) == 0) {
							gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
							cellAddrs.clear();
						}
						rowNumber++;
					}
					if((rowNumber%batchSizeInt) != 0) {
						gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
					}
					br.close();
					} else {
						while ((line = br.readLine()) != null) {
							String[] recordList = line.split(",");
							for (int i = 1; i <= recordList.length; i++) {
								cellAddrs.add(new GoogleSpreadsheetCellAddress(rowNumber, i, recordList[i - 1]));
							}							
							rowNumber++;
						}
						br.close();
						gssBatchUpdater.updateBatch(wsEntry, cellAddrs);
					}
				}
				
			} else {			

			if (filePath == null) {

				if (messageContext.getEnvelope().getBody().getFirstElement() != null) {
					String data = messageContext.getEnvelope().getBody()
							.getFirstElement().getText();
					// convert String into InputStream
					InputStream is = new ByteArrayInputStream(data.getBytes());

					// read it with BufferedReader
					BufferedReader br = new BufferedReader(
							new InputStreamReader(is));

					String line;
					int rowNumber = lastRowId;
					while ((line = br.readLine()) != null) {
						String[] recordList = line.split(",");
						for (int i = 1; i <= recordList.length; i++) {
							gssData.setCell(wsEntry, rowNumber, i,
									recordList[i - 1]);
						}
						rowNumber++;
					}
					br.close();
				}

			} else {

				BufferedReader br = new BufferedReader(new FileReader(filePath));
				String line;
				int rowNumber = lastRowId;
				while ((line = br.readLine()) != null) {
					String[] recordList = line.split(",");
					for (int i = 1; i <= recordList.length; i++) {
						gssData.setCell(wsEntry, rowNumber, i,
								recordList[i - 1]);
					}
					rowNumber++;
				}
				br.close();
			}
			}

            GoogleSpreadsheetUtils.removeTransportHeaders(messageContext);
			
			if(messageContext.getEnvelope().getBody().getFirstElement() != null) {
				messageContext.getEnvelope().getBody().getFirstElement().detach();
			}
			
			OMFactory factory   = OMAbstractFactory.getOMFactory();
	        OMNamespace ns      = factory.createOMNamespace("http://org.wso2.esbconnectors.googlespreadsheet", "ns");
	        OMElement searchResult  = factory.createOMElement("importCSVResult", ns);  
	        OMElement result      = factory.createOMElement("result", ns); 
	        searchResult.addChild(result);
	        result.setText("true");		
			messageContext.getEnvelope().getBody().addChild(searchResult);
            } else {
                ConnectException connectException = new ConnectException("Cannot import data. Worksheet with the given name is not available.");
                log.error("Error occured: " + connectException.getMessage(), connectException);
                GoogleSpreadsheetUtils.storeErrorResponseStatus(messageContext, connectException);
            }
            } else {
                ConnectException connectException = new ConnectException("Cannot import data. Spreadsheet with the given name is not available.");
                log.error("Error occured: " + connectException.getMessage(), connectException);
                GoogleSpreadsheetUtils.storeErrorResponseStatus(messageContext, connectException);
            }

		} catch (IOException te) {
			log.error("Error occurred: " + te.getMessage(), te);
			GoogleSpreadsheetUtils.storeErrorResponseStatus(
					messageContext, te);
		} catch (ServiceException te) {
			log.error("Error occurred: " + te.getMessage(), te);
			GoogleSpreadsheetUtils.storeErrorResponseStatus(
					messageContext, te);
		} catch (Exception te) {
            log.error("Error occurred: " + te.getMessage(), te);
            GoogleSpreadsheetUtils.storeErrorResponseStatus(messageContext, te);
        }
	}


}
