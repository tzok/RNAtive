import React, { useState } from "react";
import "./FileDetails.css";
import ResultTable from "./ResultTable";
const FileDetails = ({ taskId, serverAddress, filename }) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [response, setResponse] = useState(null);
  const [loading, setLoading] = useState(false);

  const toggleExpand = async () => {
    if (!isExpanded) {
      setLoading(true);
      try {
        const response = await fetch(
          `${serverAddress}/${taskId}/result/${filename}`
        );
        if (!response.ok) {
          throw new Error("Failed to fetch file details");
        }
        const data = await response.json();
        setResponse(data);
      } catch (error) {
        setResponse({ error: error.message });
      } finally {
        setLoading(false);
      }
    }
    setIsExpanded(!isExpanded);
  };

  return (
    <div className="file-details-container">
      <div className="file-details-header" onClick={toggleExpand}>
        <span className="file-name">{filename}</span>
        <span className={`arrow ${isExpanded ? "expanded" : ""}`}>▶</span>
      </div>
      {isExpanded && (
        <div className="file-details-content">
          {loading ? (
            <div className="loading-indicator">
              <div className="spinner"></div>
              <p>Loading...</p>
            </div>
          ) : response ? (
            <pre className="response-display">
              <div className="center-items-normal-txt">
                <p>Canonical pairs:</p>
              </div>
              <ResultTable ranking={response.canonicalPairs} />
              <div className="center-items-normal-txt">
                <p>Non-canonical pairs:</p>
              </div>
              <ResultTable ranking={response.nonCanonicalPairs} />
              <div className="center-items-normal-txt">
                <p>Stackings:</p>
              </div>
              <ResultTable ranking={response.stackings} />
              <div className="center-items-normal-txt">
                <p>Dot bracket:</p>
              </div>
              <div className="center-items">
                <p>{response.dotBracket}</p>
              </div>

              {/* {JSON.stringify(response, null, 2)} */}
            </pre>
          ) : (
            <p>No details available</p>
          )}
        </div>
      )}
    </div>
  );
};

export default FileDetails;
