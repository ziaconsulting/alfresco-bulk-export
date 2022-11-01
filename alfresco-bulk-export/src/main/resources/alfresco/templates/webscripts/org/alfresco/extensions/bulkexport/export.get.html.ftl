<html>
	<head>
   		<title>Export Jobs</title>
   		<style>
   			.exportSettings label {
   				float: left;
   				width: 200px;
		    }
   			.jobStatusStyle label {
   				float: left;
   				width: 500px;
		    }
		    .tooltip {
			    position: relative;
			    display: inline-block;
			    border-bottom: 1px dotted black;
			}
			
			.tooltip .tooltiptext {
			    visibility: hidden;
			    width: 500px;
			    //width: 100%;
			    //background-color: white;
			    background-color: black;
			    //color: #fff;
			    color: white;
			    text-align: center;
			    border-radius: 6px;
			    padding: 5px 0;
			    position: absolute;
			    z-index: 1;
			    top: -5px;
			    left: 110%;
			}
			
			.tooltip .tooltiptext::after {
			    content: "";
			    position: absolute;
			    top: 50%;
			    //right: 100%;
			    right: 100%;
			    margin-top: -5px;
			    border-width: 5px;
			    border-style: solid;
			    border-color: transparent black transparent transparent;
			}
			.tooltip:hover .tooltiptext {
			    visibility: visible;
			}
   		</style>
   		<script src="https://code.jquery.com/jquery-3.1.1.min.js" integrity="sha256-hVVnYaiADRTO2PzUGmuLJr8BLUSjGIZsDYGmIJLv2b8=" crossorigin="anonymous"></script>
   		<script>
   			var jobsStatusMonitor;
   			var job;
   			$(document).ready(function() {
   				startJobsStatusMonitor();
  			});
  			function startJobsStatusMonitor() {
			  var getJobsStatus = function() {
			    getJobsInfo();
			  };
			
			  jobsStatusMonitor = setInterval(getJobsStatus, 1000)
			}
			function getJobsInfo() {
				$.getJSON('${url.service}?format=json', function(data) {
					if(data.jobs === undefined) {
          				//hideElement(document.getElementById('jobs-block'));
          				hideElement(document.getElementById('jobsField'));
          				clearInterval(jobsStatusMonitor);
					} else {
          				//document.getElementById('jobsLabel').innerHTML = data.jobs
          				//document.getElementById('jobsCancel').onclick = function(){cancel(data.jobs)}; 
          				//document.getElementById('jobsCancel').value = 'Stop export';
          				$('#jobsLabel').html(data.jobs);
          				$('#jobsCancel').click(function(){cancel(data.jobs)}); 
          				$('#jobsCancel').val('Stop Export');
          				$('#totalNodesToExport').html(data.totalNodesToExport);
          				$('#availableNodesToExport').html(data.availableNodesToExport);
          				$('#previouslyExportedNodes').html(data.previouslyExportedNodes);
       					//showElement(document.getElementById('jobs-block'), false);
       					showElement(document.getElementById('jobsField'), true);
					}
				})
				.fail(function(jqxhr, textStatus, error) {
					document.getElementById("exportOutput").innerHTML = "Unexpected Error:"+error;
				});
			}
			function hideElement(element) {
			  element.style.display = "none";
			}
			function showElement(element, inline) {
			  if (inline) {
			    element.style.display = "inline";
			  } else {
			    element.style.display = "inline-block";
			  }
			}
			  			
      		function cancel(jobId) {
				$.getJSON('${url.service}?format=json&cancel='+jobId, function(data) {
					var outputHtml = data.output.replace(/(?:\\r\\n|\\r|\\n)/g, '<br />');
	   				document.getElementById("exportOutput").innerHTML = outputHtml;
				})
				.fail(function(jqxhr, textStatus, error) {
					document.getElementById("exportOutput").innerHTML = "Unexpected Error on cancel:"+error;
				});
      		}
      		function exportJob() {
      	 		var xhr = new XMLHttpRequest();
      	 		var noderef = document.getElementById("nodeRef").value;
      	 		var aspects = document.getElementById("aspects").value;
      	 		var properties = document.getElementById("properties").value;
      	 		var fromDate = document.getElementById("fromDate").value;
      	 		var toDate = document.getElementById("toDate").value;
      	 		var base = document.getElementById("base").value;
      	 		var updateTypesOrAspectsOrProperties = document.getElementById("updateTypesOrAspectsOrProperties").value;
      	 		var updateModelPrefix = document.getElementById("updateModelPrefix").value;
      	 		var ignoreexported = document.getElementById("ignoreExported").checked;
      	 		var exportversions = document.getElementById("exportVersions").checked;
      	 		var revisionhead = document.getElementById("revisionHead").checked;
      	 		var usenodecache = document.getElementById("useNodeCache").checked;
         		var exportJsonUrl = '${url.service}?format=json&nodeRef='+noderef+'&aspects='+aspects+'&properties='+properties+'&updateTypesOrAspectsOrProperties='+updateTypesOrAspectsOrProperties+'&updateModelPrefix='+updateModelPrefix+'&fromDate='+fromDate+'&toDate='+toDate+'&base='+base+'&ignoreExported='+ignoreexported+'&exportVersions='+exportversions+'&revisionHead='+revisionhead+'&useNodeCache='+usenodecache;
				$.getJSON(exportJsonUrl, function(data) {
					var outputHtml = data.output.replace(/(?:\\r\\n|\\r|\\n)/g, '<br />');
	   				document.getElementById("exportOutput").innerHTML = outputHtml;
	   				if ( data && data.totalNodesToExport )
      					$('#totalNodesToExport').html(data.totalNodesToExport);
      				if ( data && data.availableNodesToExport )
      					$('#availableNodesToExport').html(data.availableNodesToExport);
      				if ( data && data.previouslyExportedNodes )
      					$('#previouslyExportedNodes').html(data.previouslyExportedNodes);
				})
				.fail(function(jqxhr, textStatus, error) {
					document.getElementById("exportOutput").innerHTML = "Unexpected Error on export:"+error;
				});
				startJobsStatusMonitor();
      		}
   		</script>
	</head>
	<body>
		<h3>Running Export Jobs</h3>
		<form id="bulkExportForm">
	      <fieldset class='exportSettings'><legend>Export Settings</legend>
	        <p><label for="nodeRef">Folder Noderef:</label><div class="tooltip"> <input type="text" id="nodeRef" name="nodeRef" title="Enter NodeRef of folder to be exported" size="80" required/><span class="tooltiptext">Enter NodeRef of folder to be exported</span></div></p>
	        <p><label for="base">Output folder:</label><div class="tooltip"> <input type="text" id="base" name="base" size="80" required/><span class="tooltiptext">Export folder for Alfresco node and children</span></div></p>
	        <p><label for="fromDate">From date:</label><div class="tooltip"> <input type="text" id="fromDate" name="fromDate" size="80" /><span class="tooltiptext">Export nodes based on Date. Example: 2017-03-01T00:00:00</span></div></p>
	        <p><label for="toDate">To date:</label><div class="tooltip"> <input type="text" id="toDate" name="toDate" size="80" /><span class="tooltiptext">Export nodes based on Date. Example 2017-04-01T00:00:00</span></div></p>
	        <p><label for="aspects">Add aspects:</label> <div class="tooltip"><input type="text" id="aspects" name="aspects" size="80" /> <span class="tooltiptext">Inject custom aspects(Eg: my:aspectTest,my:aspectTemp)</span></div></p>
	        <p><label for="properties">Add properties:</label><div class="tooltip"> <input type="text" id="properties" name="properties" size="80" /> <span class="tooltiptext">Inject custom properties (Eg: my:prop1=TESTEXP,my:prop2=cm:name)</span></div></p>
	        <p><label for="updateTypesOrAspectsOrProperties">Update existing model data:</label><div class="tooltip"> <input type="text" id="updateTypesOrAspectsOrProperties" name="updateTypesOrAspectsOrProperties" size="80" /> <span class="tooltiptext">Update existing types/aspects/properties (Eg: my:prop1=mynew:prop2,my:type1=mynew:type1)</span></div></p>
	        <p><label for="updateModelPrefix">Update model prefix:</label><div class="tooltip"> <input type="text" id="updateModelPrefix" name="updateModelPrefix" size="80" /> <span class="tooltiptext">Update model prefix on types/aspects/properties (Eg: oldprefix=newprefix,op=newp)</span></div></p>
	        <p><label for="ignoreExported">Ignore Exported:</label> <div class="tooltip"><input type="checkbox" id="ignoreExported" name="ignoreExported" value="true" unchecked/> <span class="tooltiptext">checked means that existing files that are exported will NOT be replaced</span></div></p>
	        <p><label for="exportVersions">Export Versions:</label> <div class="tooltip"><input type="checkbox" id="exportVersions" name="exportVersions" value="true" unchecked/> <span class="tooltiptext">checked means that we should export all versions of the nodes</span></div></p>
	        <p><label for="revisionHead">Revision Head:</label> <div class="tooltip"><input type="checkbox" id="revisionHead" name="revisionHead" value="true" unchecked/> <span class="tooltiptext">checked (and exportVersion=true) means that the head revision will be numbered. unchecked means the default version numbering scheme is used</span></div></p>
	        <p><label for="useNodeCache">Use Node Cache:</label><div class="tooltip"> <input type="checkbox" id="useNodeCache" name="useNodeCache" value="true" unchecked/> <span class="tooltiptext">checked means that the list of nodes are cached to the export base for future use</span></div></p>
	      </fieldset>
	      <p><button type="button" onclick="exportJob();">Initiate Bulk Export</button></p>
	    </form>
		<div id='jobs-block' class='jobStatusStyle'>
	      <fieldset><legend>Running Job Status</legend>
	      	<p id='jobsField' style='display:none'><label for='jobsCancel' id='jobsLabel'>No Jobs</label><input type='button' id='jobsCancel' value='Oops..You should not see this'/></p>
		    <p><label for='totalNodesToExport' id='totalNodesToExportLabel'>Total number of nodes to export:</label><label id='totalNodesToExport'>N/A</label></p>
		    <p><label for='availableNodesToExport' id='availableNodesToExportLabel'>Available number of nodes to still export:</label><label id='availableNodesToExport'>N/A</label></p>
		    <p><label for='previouslyExportedNodes' id='previouslyExportedNodeslabel'>Previously exported Nodes count:</label><label id='previouslyExportedNodes'>N/A</label></p>
	      </fieldset>
   		</div>
    	<div id="exportOutput"></div>
	</body>
</html>