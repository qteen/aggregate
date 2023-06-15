/*
 * Copyright (C) 2010 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.aggregate.format.table;

import org.opendatakit.aggregate.client.filter.FilterGroup;
import org.opendatakit.aggregate.client.submission.Column;
import org.opendatakit.aggregate.client.submission.SubmissionUISummary;
import org.opendatakit.aggregate.constants.common.FormElementNamespace;
import org.opendatakit.aggregate.constants.format.FormatConsts;
import org.opendatakit.aggregate.datamodel.FormElementModel;
import org.opendatakit.aggregate.form.IForm;
import org.opendatakit.aggregate.format.Row;
import org.opendatakit.aggregate.format.SubmissionFormatter;
import org.opendatakit.aggregate.format.element.ElementFormatter;
import org.opendatakit.aggregate.format.element.LinkElementFormatter;
import org.opendatakit.aggregate.server.GenerateHeaderInfo;
import org.opendatakit.aggregate.servlet.FormMultipleValueServlet;
import org.opendatakit.aggregate.submission.*;
import org.opendatakit.aggregate.submission.type.jr.JRDateTimeType;
import org.opendatakit.aggregate.submission.type.jr.JRTemporal;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.web.CallingContext;
import org.opendatakit.common.web.constants.BasicConsts;

import java.io.PrintWriter;
import java.util.*;

public class CsvMultipleFormatterWithFilters implements SubmissionFormatter {
  private final IForm form;
  private final PrintWriter output;
  private ElementFormatter elemFormatter;
  private List<FormElementModel> propertyNames;
  private List<String> headers;
  private List<FormElementNamespace> namespaces;
  private HashMap<String, List<HashMap>> csvMap = new HashMap();

  public CsvMultipleFormatterWithFilters(IForm xform, String webServerUrl, PrintWriter printWriter,
                                         FilterGroup filterGroup) {
    form = xform;
    output = printWriter;

    headers = new ArrayList<String>();
    SubmissionUISummary summary = new SubmissionUISummary(form.getViewableName());

    GenerateHeaderInfo headerGenerator = new GenerateHeaderInfo(filterGroup, summary, form);
    headerGenerator.processForHeaderInfo(form.getTopLevelGroupElement());
    propertyNames = headerGenerator.getIncludedElements();
    namespaces = headerGenerator.includedFormElementNamespaces();

    for (Column col : summary.getHeaders()) {
      headers.add(col.getDisplayHeader());
    }
    elemFormatter = new LinkElementFormatter(webServerUrl, FormMultipleValueServlet.ADDR, true,
        true, true, false);
  }

  @Override
  public final void beforeProcessSubmissions(CallingContext cc) {
    // format headers
    appendCsvRow(headers.iterator());
  }

  @Override
  public final void processSubmissionSegment(List<Submission> submissions,
                                             CallingContext cc) throws ODKDatastoreException {
    List<HashMap> csvMap_list = csvMap.get("ROOT");
    if(csvMap_list==null)
      csvMap_list = new LinkedList<HashMap>();

    HashMap<String, String> hasil = new LinkedHashMap<>();
    // format row elements
    for (Submission sub : submissions) {
      hasil.clear();
      List<SubmissionValue> submissionValues = sub.getSubmissionValues();
      for (SubmissionValue submissionValue : submissionValues) {
        if(submissionValue instanceof SubmissionField) {
          Object submissionData = ((SubmissionField) submissionValue).getValue();
          if(submissionData!=null && submissionData instanceof JRTemporal) {
            hasil.put(submissionValue.getPropertyName(), ((JRTemporal) submissionData).getRaw());
          } else {
            hasil.put(submissionValue.getPropertyName(), submissionData==null?null:submissionData.toString());
          }
        } else if(submissionValue instanceof SubmissionRepeat){
          SubmissionRepeat submissionRepeat = (SubmissionRepeat) submissionValue;
          processRepeat(submissionRepeat, sub);
        }
      }

      csvMap_list.add((HashMap) hasil.clone());
      csvMap.put("ROOT", csvMap_list);
    }
  }

  @Override
  public final void afterProcessSubmissions(CallingContext cc) {
  }

  @Override
  public final void processSubmissions(List<Submission> submissions, CallingContext cc) throws ODKDatastoreException {
    beforeProcessSubmissions(cc);
    processSubmissionSegment(submissions, cc);
    afterProcessSubmissions(cc);
  }

  /**
   * Helper function used to append the comma separated value row
   *
   * @param itr string values to be separated by commas
   */
  private void appendCsvRow(Iterator<String> itr) {
    output.append(BasicConsts.EMPTY_STRING);
    while (itr.hasNext()) {
      String value = itr.next();
      if (value != null) {
        // escape double quotes with another double quote per RFC 4180
        value = value.replaceAll(BasicConsts.QUOTE, BasicConsts.QUOTE_QUOTE);
        output.append(BasicConsts.QUOTE).append(value).append(BasicConsts.QUOTE);
      }
      if (itr.hasNext()) {
        output.append(FormatConsts.CSV_DELIMITER);
      } else {
        output.append(BasicConsts.NEW_LINE);
      }
    }
  }

  private void processRepeat(SubmissionRepeat submissionRepeat, SubmissionSet sub) {
    List<HashMap> csvMap_repeat_list = csvMap.get(submissionRepeat.getFormElementModel().getElementName());
    if(csvMap_repeat_list==null)
      csvMap_repeat_list = new LinkedList<HashMap>();

    HashMap<String, String> hasil_repeat = new LinkedHashMap<>();
    List<SubmissionSet> submissionSets = submissionRepeat.getSubmissionSets();
    for (Iterator iterator2 = submissionSets.iterator(); iterator2
            .hasNext();) {
      SubmissionSet submissionSet = (SubmissionSet) iterator2
              .next();
      hasil_repeat.clear();
      hasil_repeat.put("uid_parent", sub.getKey().getKey());
      hasil_repeat.put("uid", submissionSet.getKey().getKey());

      List<SubmissionValue> submissionRepValues = submissionSet.getSubmissionValues();
      for (Iterator itr = submissionRepValues.iterator(); itr
              .hasNext();) {
        SubmissionValue submissionRepValue = (SubmissionValue) itr
                .next();
        if(submissionRepValue instanceof SubmissionField) {
          SubmissionField submissionData = (SubmissionField) submissionRepValue;
          hasil_repeat.put(submissionData.getPropertyName(), submissionData.getValue()==null?null:submissionData.getValue().toString());
        } else if(submissionRepValue instanceof SubmissionRepeat){
          SubmissionRepeat submissionRepRepeat = (SubmissionRepeat) submissionRepValue;
          processRepeat(submissionRepRepeat, submissionSet);
        }
      }
      csvMap_repeat_list.add((HashMap) hasil_repeat.clone());
      csvMap.put(submissionRepeat.getFormElementModel().getElementName(), csvMap_repeat_list);
    }
  }

  public HashMap<String, List<HashMap>> getCsvMap() {
    return csvMap;
  }
}
