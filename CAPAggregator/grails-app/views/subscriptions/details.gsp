<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <meta name="layout" content="main"/>
    <title></title>

    <asset:link rel="icon" href="favicon.ico" type="image/x-ico" />
</head>
<body>

  <g:if test="${subscription!=null}">
    <div class="container-fluid">
      <div class="row">
        <div class="container-fluid">
          <h1>${subscription.subscriptionId} / ${subscription.subscriptionName}</a></h1>
  
          <div class="panel panel-default">
            <div class="panel-heading">
              <h3 class="panel-title">Subscription Info</h3>
            </div>
            <div class="panel-body form-horizontal container-fluid">
              <div class="row">
                <div class="col-md-9">
                  <div class="row">
                    <div class="col-md-4">
                      <div class="form-group">
                        <label class="col-sm-3 control-label">URL</label>
                        <div class="col-sm-9"><p class="form-control-static"><a href="${subscription.subscriptionUrl}">${subscription.subscriptionUrl}</a></p></div>
                      </div>
                      <div class="form-group">
                        <label class="col-sm-3 control-label">Alert Count</label>
                        <div class="col-sm-9"><p class="form-control-static">${totalAlerts?:0} (${rows?.size()?:0} shown)</p></div>
                      </div>
                    </div>
    
    
                    <div class="col-md-4">
                      <div class="form-group">
                        <label class="col-sm-3 control-label">Filter Type</label>
                        <div class="col-sm-9"><p class="form-control-static">${subscription.filterType}</p></div>
                      </div>
    
                      <div class="form-group">
                        <label class="col-sm-3 control-label">Filter Geometry</label>
                        <div class="col-sm-9"><p class="form-control-static" id="sub-filter-geom" data-sub-geom="${subscription.filterGeometry}" >${subscription.filterGeometry}</p></div>
                      </div>
                    </div>

                    <div class="col-md-4">
                      <div class="form-group">
                        <label class="col-sm-3 control-label">Params</label>
                        <div class="col-sm-9"><p class="form-control-static"><ul>
                            <li>langFilter:<strong>${subscription.languageOnly}</strong></li>
                            <li>priorityFilter:<strong>${subscription.highPriorityOnly}</strong></li>
                            <li>officialFilter:<strong>${subscription.officialOnly}</strong></li>
                            <li>xpathFilterId:<strong>${subscription.xPathFilterId}</strong></li>
                          </ul>
                        </p></div>
                      </div>
                    </div>

                  </div>
                  <div class="row">
                    <div class="col-md-12">
                      <div class="form-group">
                        <label class="col-sm-2 control-label">WebHooks</label>
                        <div class="col-sm-10"><p class="form-control-static"><ul>
                          <g:each in="${subscription.hooks}" var="webhook">
                            <div class="card">
                              <div class="card-body">
                                ${webhook.hookUrl}
                              </div>
                            </div>
                          </g:each>
                        </div>
                      </div>
                    </div>
                

                    <sec:ifLoggedIn>
                      <div class="card">
                        <div class="card-body">
                          <g:form controller="subscriptions" id="${params.id}" action="addHook" method="get" class="container">
                            <div class="input-group">
                                <span class="input-group-addon">Add Web Hook:</span>
                                <input type="text" name="newHookUrl" class="form-control " placeholder="Text input" value="">
                                <span class="input-group-btn">
                                    <button type="submit" class="btn btn-search">Add</button>
                                </span>
                            </div>
                          </g:form>
                        </div>
                      </div>
                    </sec:ifLoggedIn>

                  </div>
                </div>

                <div class="col-md-3">
                  <div class="MapWithFeatures" id="subscription_map" data-features="[]"></div>

                </div>
    
              </div>


            </div>
          </div>
  
        </div>
      </div>
  
      <div class="row">
  
        <div class="container-fluid" style="vertical-align: middle; text-align:center;">
  
  
          <g:form controller="subscriptions" id="${params.id}" action="details" method="get" class="container">
            <div class="input-group">
                <input type="text" name="q" class="form-control " placeholder="Text input" value="${params.q}">
                <span class="input-group-btn">
                    <button type="submit" class="btn btn-search">Search</button>
                </span>
            </div>
          </g:form>
  
          <div>
            Showing alerts ${(offset?:0)+1} to ${java.lang.Math.min(((offset?:0)+(max?:10)),(totalAlerts?:0))} of ${totalAlerts?:0}
          </div>
  
          <div class="pagination">
            <g:paginate controller="subscriptions" action="details" params="${[id:params.id]}" total="${totalAlerts?:0}" next="Next" prev="Previous" omitNext="false" omitPrev="false" />
          </div>
  
        </div>
        <div class="container-fluid">
  
          <table class="table table-bordered table-striped">
            <thead>
              <tr>
              </tr>
            </thead>
            <tbody>
              <g:each in="${rows}" var="alert" status="s">
                <g:set var="alsrc" value="${alert.getSourceAsMap()}"/>
                <tr>
                  <td>
                    <div class="MapWithAlert" id="map_for_${s}"
                                              data-alert-id="${alert.getId()}" 
                                              data-alert-body="${alsrc.AlertBody as grails.converters.JSON}"></div>
                  </td>
                  <td>
                    <g:set var="ifo_list" value="${alsrc.AlertBody.info instanceof List ? alsrc.AlertBody.info : [ alsrc.AlertBody.info ]}"/>
                    <g:each in="${ifo_list}" var="ifo">
                      <h3>${ifo.headline}</h3>
                    </g:each>
  
                    <div class="form-horizontal">
                      <div class="form-group"> <label class="col-sm-2 control-label">Alert Identifier</label> 
                        <div class="col-sm-10"><p class="form-control-static"><g:link controller="alert" action="details" id="${alsrc.AlertBody.identifier}">${alsrc.AlertBody.identifier}</g:link><g:if test="${alsrc.AlertMetadata.Expires != null}">&nbsp;(Expires: ${alsrc.AlertMetadata.Expires})</g:if></p></div>
                      </div>
                      
                      <div class="form-group"> 
                        <label class="col-sm-2 control-label">Alert Sender</label> 
                        <div class="col-sm-10">
                          <p class="form-control-static">${alsrc.AlertBody.sender} (via feed 
                            <a href="/feedFacade/sourcefeed/feed/${alsrc.AlertMetadata.sourceFeed}">${alsrc.AlertMetadata.sourceFeed}</a>)</p>
                        </div> 
                      </div>
                      <div class="form-group"> <label class="col-sm-2 control-label">Times:</label> <div class="col-sm-10">
                          <table class="table table-striped">
                            <tr><th>Date on alert</th><td>${alsrc.AlertBody.sent}</td></tr>
                            <g:each in="${alsrc.AlertMetadata.CCHistory}" var="he">
                              <tr><th>${he.event}</th><td><g:formatDate format="yyyy-MM-dd HH:mm:ss.SSS z" date="${new Date(he.timestamp)}" timeZone="UTC"/></td></tr>
                            </g:each>
                          </table>
                       </div> </div>
                      <div class="form-group"> <label class="col-sm-2 control-label">Source</label> <div class="col-sm-10"><p class="form-control-static">${alsrc.AlertMetadata.SourceUrl}</p></div> </div>
                      <div class="form-group"> <label class="col-sm-2 control-label">Matched Subscriptions</label> <div class="col-sm-10"><p class="form-control-static">
                       <g:each in="${alsrc.AlertMetadata.MatchedSubscriptions}" var="ms"> <g:link controller="subscriptions" action="details" id="${ms}">${ms}</g:link> &nbsp; </g:each> </p></div> 
                      </div>
                      <div class="form-group"> <label class="col-sm-2 control-label">Tags</label> 
                        <div class="col-sm-10">
                          <p class="form-control-static"><g:each in="${alsrc.AlertMetadata.tags}" var="tag">${tag} &nbsp; </g:each>
                        </div> 
                      </div>
                      <g:each in="${ifo_list}" var="ifo">
                        <div class="form-group"> <label class="col-sm-2 control-label">Information</label> <div class="col-sm-10">
                              <div class="form-group"> <label class="col-sm-2 control-label">Language</label> <div class="col-sm-10"><p class="form-control-static">${ifo.language}</p></div> </div>
                              <div class="form-group"> <label class="col-sm-2 control-label">Headline</label> <div class="col-sm-10"><p class="form-control-static">${ifo.headline}</p></div> </div>
                              <div class="form-group"> <label class="col-sm-2 control-label">Description</label> <div class="col-sm-10"><p class="form-control-static">${ifo.description}</p></div> </div>
                              <div class="form-group"> <label class="col-sm-2 control-label">Action</label> <div class="col-sm-10"><p class="form-control-static">${ifo.action}</p></div> </div>
                        </div>
                      </g:each>
                    </div>
  
                  </td>
                </tr>
              </g:each>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </g:if>
  <g:else>
    <div class="container-fluid">
      <div class="row">
        No Unfiltered Subscription Found. Please load the default subscriptions using the <g:link controller="admin" action="syncSubList">admin - load subscription</g:link> feature
      </div>
    </div>
  </g:else>

<asset:script type="text/javascript">
  if (typeof jQuery !== 'undefined') {
    (function($) {

      let sub_geom = $('#sub-filter-geom').data("sub-geom");
      let sub_poly = [];

      console.log("Sub poly is %o",sub_geom);

      $('.MapWithAlert').each(function(i,obj) {
        initMap(obj.id, $(obj).data("alert-body"));
      });

      $('.MapWithFeatures').each(function(i,obj) {
        console.log("Map with features");
        mapWithGeoJsonFeatures(obj.id, sub_geom);
      });
    })(jQuery);
  }

</asset:script>

</body>
</html>
