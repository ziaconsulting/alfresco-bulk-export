[#ftl]
{
  [#if output??]
  "output" : "${output}"
  [/#if]
  [#if output?? && jobs??]
  ,
  [/#if]
  [#if jobs??]
  "jobs" : "${jobs}"
  [/#if]
  [#if (output?? || jobs??) && totalNodesToExport??]
  ,
  [/#if]
  [#if totalNodesToExport??]
  "totalNodesToExport" : "${totalNodesToExport}"
  [/#if]
  [#if (output?? || jobs?? || totalNodesToExport??) && availableNodesToExport??]
  ,
  [/#if]
  [#if availableNodesToExport??]
  "availableNodesToExport" : "${availableNodesToExport}"
  [/#if]
  [#if (output?? || jobs?? || totalNodesToExport?? || availableNodesToExport??) && previouslyExportedNodes??]
  ,
  [/#if]
  [#if previouslyExportedNodes??]
  "previouslyExportedNodes" : "${previouslyExportedNodes}"
  [/#if]
}