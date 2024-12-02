import React, { useState } from "react";
import "./SendingTest.css";
import SvgImg from "./SvgImg";
import ResultTable from "./ResultTable";
import { useDropzone } from "react-dropzone";
import FileDetails from "./FileDetails";
import TextInput from "./TextInput2";
function SendingTest() {
  const [text2, setText2] = useState("");
  const handleTextChange2 = (newText) => {
    setText2(newText); // Update the text state
  };
  const [isLoading, setIsLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [serverError, setServerError] = useState(null);

  // Example uploaded files state and other required states
  const [uploadedFiles, setUploadedFiles] = useState([]);

  const [taskIdComplete, setTaskIdComplete] = useState(null);

  const onDrop = (acceptedFiles) => {
    setUploadedFiles((prevFiles) => [
      ...prevFiles,
      ...acceptedFiles.map((file) => ({
        id: file.name + Date.now(), // Unique id using name and timestamp
        file,
      })),
    ]);
  };

  const removeFile = (fileId) => {
    setUploadedFiles((prevFiles) => prevFiles.filter((f) => f.id !== fileId));
  };
  const { getRootProps, getInputProps, isDragActive } = useDropzone({ onDrop });

  const serverAddress = "http://localhost:8080/api/compute"; // Replace with actual server address
  const analyzer = "MCANNOTATE"; // Replace with actual analyzer value
  const visualizationTool = "VARNA"; // Replace with actual tool value
  const consensusMode = "CANONICAL";
  const confidenceLevel = "0.5";
  const molprobityFilter = "true";

  const handleSendData = async (taskIdArg = "") => {
    const POLL_INTERVAL = 3000; // 3 seconds
    let taskId = taskIdArg || null;

    try {
      // Step 1: Create and send the payload if taskId is not provided
      if (!taskId) {
        setIsLoading(true);
        setResponse(null);
        setServerError(null);

        // Log file details
        uploadedFiles.forEach(async (fileObj, index) => {
          console.log(`File ${index + 1}:`);
          console.log("ID:", fileObj.id);
          console.log("Name:", fileObj.file.name);
          console.log("Size:", fileObj.file.size, "bytes");
          console.log("Type:", fileObj.file.type);
          console.log("Text: ", await fileObj.file.text());
        });

        // Prepare the files data
        const files = await Promise.all(
          uploadedFiles.map(async (fileObj) => ({
            name: fileObj.file.name,
            content: await fileObj.file.text(), // Reads file content as text
          }))
        );

        // Prepare the payload
        const payload = {
          files,
          analyzer,
          visualizationTool,
          consensusMode,
          confidenceLevel,
          molprobityFilter,
        };

        // Send the payload
        const response = await fetch(serverAddress, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(payload),
        });

        if (!response.ok) {
          throw new Error(`Server responded with status ${response.status}`);
        }

        const { taskId: newTaskId } = await response.json();
        taskId = newTaskId;
        console.log("Task submitted. Task ID:", taskId);
      } else {
        console.log(`Using provided Task ID: ${taskId}`);
      }

      // Step 2: Poll for task status
      const status = await pollTaskStatus(taskId, POLL_INTERVAL, setResponse);

      // Step 3: Handle the result based on task status
      if (status === "COMPLETED") {
        await fetchTaskResult(taskId);
      }
    } catch (error) {
      console.error("Error in submission process:", error.message);
      if (taskId) {
        console.error(`Task ID: ${taskId}`);
      }
    }
  };

  const pollTaskStatus = async (taskId, pollInterval, setResponse) => {
    while (true) {
      const statusResponse = await fetch(`${serverAddress}/${taskId}/status`, {
        method: "GET",
      });

      if (!statusResponse.ok) {
        throw new Error(
          `Failed to get status. Status: ${statusResponse.status}`
        );
      }

      const statusData = await statusResponse.json();
      console.log("Task status:", statusData);

      const { status, message } = statusData;

      if (status === "FAILED") {
        console.error("Task failed:", message);
        setResponse({
          error: message || "Task failed with no additional message.",
        });
        return;
      }

      if (status === "COMPLETED") {
        console.log("Task completed successfully!");
        await fetchTaskResult(taskId, setResponse);
        return;
      }

      // If still processing, wait and try again
      console.log("Task still processing. Retrying...");
      await new Promise((resolve) => setTimeout(resolve, pollInterval));
    }
  };

  const fetchTaskResult = async (taskId, setResponse) => {
    try {
      const resultResponse = await fetch(`${serverAddress}/${taskId}/result`, {
        method: "GET",
      });

      if (!resultResponse.ok) {
        throw new Error(
          `Failed to get result. Status: ${resultResponse.status}`
        );
      }

      const resultData = await resultResponse.json();
      console.log("Task result:", resultData);

      // Set the response state to trigger UI update
      setResponse(resultData);
    } catch (error) {
      console.error("Error fetching task result:", error.message);
      setResponse({ error: error.message });
    } finally {
      setIsLoading(false);
      setTaskIdComplete(taskId);
    }
  };

  const renderContent = () => {
    if (isLoading) {
      return (
        <div className="spinner-container">
          <div className="spinner"></div>
          <p>Sending data...</p>
        </div>
      );
    }

    if (serverError) {
      return (
        <div className="error-container">
          <h2>Error</h2>
          <p>{serverError}</p>
          <button className="reset-button" onClick={() => setServerError(null)}>
            Retry
          </button>
        </div>
      );
    }

    if (response) {
      return (
        <div className="response-container">
          <h2>Server Response</h2>
          <p>Your unique code: {taskIdComplete}</p>
          <h1>SVG Viewer</h1>
          <SvgImg serverAddress={serverAddress} taskId={taskIdComplete} />

          <h1>Result Table Test</h1>
          <ResultTable ranking={response.canonicalPairs} />
          <h1>Result Table Test</h1>
          <ResultTable ranking={response.stackings} />
          <h1>Result Table</h1>
          <ResultTable ranking={response.ranking} />
          {response.fileNames.map((filename, index) => (
            <FileDetails
              key={index}
              taskId={taskIdComplete}
              serverAddress={serverAddress}
              filename={filename}
            />
          ))}
          {/* {response.filenames.map((filename, index) => (
            <FileDetails
              key={index}
              taskId={taskIdComplete}
              serverAddress={serverAddress}
              filename={filename}
            />
          ))} */}
          {/* <FileDetails
            key={0}
            taskId={taskIdComplete}
            serverAddress={serverAddress}
            filename={"A2.pdb"}
          /> */}
          {/* <pre>{JSON.stringify(response, null, 2)}</pre> */}
          <button className="reset-button" onClick={() => setResponse(null)}>
            Reset
          </button>
        </div>
      );
    }

    // Default view with the send button
    return (
      <div>
        <div className="home-container">
          <div className="dropzone-container" {...getRootProps()}>
            <input {...getInputProps()} />
            {isDragActive ? (
              <p>Drop the files here ...</p>
            ) : (
              <p>Drag & drop files here, or click to select files</p>
            )}
          </div>

          <div className="file-list">
            {uploadedFiles.map((fileWrapper) => (
              <div key={fileWrapper.id} className="file-item">
                <span
                  style={{
                    fontSize:
                      "18px" /* This font size is set using a 'string value' */,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  {fileWrapper.file.name}
                </span>
                <button
                  className="delete-button"
                  onClick={() => removeFile(fileWrapper.id)}
                >
                  X
                </button>
              </div>
            ))}
          </div>
        </div>
        <button className="send-button" onClick={() => handleSendData()}>
          Send Data
        </button>
        <p>or get former results</p>
        <TextInput value={text2} onTextChange={handleTextChange2} />
        <button className="send-button" onClick={() => handleSendData(text2)}>
          Get results
        </button>
      </div>
    );
  };

  return (
    <div className="App">
      <h1>Data Sender</h1>
      {renderContent()}
    </div>
  );
}

export default SendingTest;
