import logo from "./logo.svg";
import "./App.css";
// Home.js
import React, { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useDropzone } from "react-dropzone";
import "./Home.css";
import SelectableList from "./SelectableList";
import Slider from "./Slider";
import Checkbox from "./Checkbox";
import TextInput from "./TextInput2";
import TextInputWide from "./TextInputWide";
import Dropdown from "./Dropdown";
// import { REACT_APP_SERVER_ADDRESS } from "./config.js";

import "./SendingTest.css";
import SvgImg from "./SvgImg";
import ResultTable from "./ResultTable";
import FileDetails from "./FileDetails";
import * as configs from "./config";

//rnapuzzles standarized submissions najlepiej brać stamtąd RP 18 <--- example czy cos
//specjalny riquest na serwer że rób przykład

//parametry do wysłania -- jakiś suwak na to
//confidence level -- od 0 do 1, domyślnie 0.5 antdizajn ma c:

//wybór z listy 6-7 anotatorów, jak w RNApdbee (identify basepairs using) C:

//consensus mode: CANONICAL (domyślny), NON_CANONICAL , STACKING, ALL    c:

// Jednak dodajmy do tego jeszcze jeden parametr - narzędzie do wizualizacji, jedno z:   C:
// VARNA (domyślne),RNApuzzler,PseudoViewer,R-Chie

//opcjonalny parametr dot bracket -- string

//jakiś taki checkbox czy dokonać filtrowania mol probity, domyślnie tak. c:

//tabelka z rank, nazwa + punkty
//https://ant.design/components/table <-taka  ogółem wszystko stąd
//tabelka confidance table cannonical
//tabelki dla par kanonicznych niekanonicznch i stackingu --> dopiero jak się zapytam o model dany to dostane dane
//może być svg wizualizacji struktury 2gorzędowej

//sprawdzić czy github mi dodaje windowsowe entery!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

function Home() {
  //getting the id from params
  const { id } = useParams();
  const navigate = useNavigate();

  //sending
  const [text2, setText2] = useState("");
  const handleTextChange2 = (newText) => {
    setText2(newText); // Update the text state
  };
  const [isLoading, setIsLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [removalReasondisp, setRemovalReasondisp] = useState(null);

  const [serverError, setServerError] = useState(null);

  // Example uploaded files state and other required states
  const [taskIdComplete, setTaskIdComplete] = useState(null);

  const serverAddress = configs.default.SERVER_ADDRESS; // REACT_APP_SERVER_ADDRESS; // process.env.REACT_APP_SERVER_ADDRESS; //"http://localhost:8080/api/compute"; // Replace with actual server address
  const analyzer = "MCANNOTATE"; // Replace with actual analyzer value
  const visualizationTool = "VARNA"; // Replace with actual tool value
  const consensusMode = "CANONICAL";
  const confidenceLevel = "0.5";
  const molProbityFilter = "GOOD_AND_CAUTION";

  const handleSendData = async (pload = [], taskIdArg = "") => {
    const POLL_INTERVAL = 3000; // 3 seconds
    let taskId = taskIdArg || null;
    console.log("SERVER:", serverAddress);

    try {
      // Step 1: Create and send the payload if taskId is not provided
      if (!taskId) {
        setIsLoading(true);
        setResponse(null);
        setServerError(null);
        setRemovalReasondisp(null);

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
          molProbityFilter,
        };
        if (pload != []) {
          payload.analyzer = pload[0];
          payload.visualizationTool = pload[1];
          payload.consensusMode = pload[2];
          if (payload[3] == 0) {
            //enable fuzzy mode
            payload.confidenceLevel = null;
          } else {
            payload.confidenceLevel = parseFloat(pload[3]);
          }

          payload.molProbityFilter = pload[4]; //pload[4];
          if (pload[5] != "") {
            payload.dotBracket = pload[5];
          }
        }

        //   payload = {
        //     "files": [
        //         {
        //             "name": f.name,
        //             "content": f.read_text(),
        //         }
        //         for f in files
        //     ],
        //     "analyzer": args.analyzer,
        //     "visualizationTool": args.visualization,
        //     "consensusMode": args.consensus_mode,
        //     "confidenceLevel": args.confidence,
        //     "molProbityFilter": args.molprobity_filter,
        // }

        // if dot_bracket:
        //     payload["dotBracket"] = dot_bracket

        // [MCANNOTATE, BARNABA, RNAVIEW, FR3D, BPNET, RNAPOLIS]
        switch (payload.analyzer) {
          case "MC-Annotate":
            payload.analyzer = "MCANNOTATE";
            break;
          case "BARNABA":
            payload.analyzer = "BARNABA";
            break;
          case "RNAview":
            payload.analyzer = "RNAVIEW";
            break;
          case "FR3D":
            payload.analyzer = "FR3D";
            break;
          case "BPnet":
            payload.analyzer = "BPNET";
            break;
          case "RNApolis":
            payload.analyzer = "RNAPOLIS";
            break;
        }

        // [ALL, STACKING, CANONICAL, NON_CANONICAL]]
        //"Canonical", "Non-canonical", "Stacking", "All"
        switch (payload.consensusMode) {
          case "MC-All":
            payload.consensusMode = "ALL";
            break;
          case "Stacking":
            payload.consensusMode = "STACKING";
            break;
          case "Canonical":
            payload.consensusMode = "CANONICAL";
            break;
          case "Non-canonical":
            payload.consensusMode = "NON_CANONICAL";
            break;
        }
        //[PSEUDOVIEWER, VARNA, RCHIE, RNAPUZZLER]]
        //"VARNA", "RNApuzzler", "PseudoViewer", "R-Chie"]
        switch (payload.visualizationTool) {
          case "VARNA":
            payload.visualizationTool = "VARNA";
            break;
          case "RNApuzzler":
            payload.visualizationTool = "RNAPUZZLER";
            break;
          case "PseudoViewer":
            payload.visualizationTool = "PSEUDOVIEWER";
            break;
          case "R-Chie":
            payload.visualizationTool = "RCHIE";
            break;
        }
        console.log("TEST PAYLOAD:", payload.molProbityFilter);
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

        navigate(`/${newTaskId}`, { replace: true }); //navigate to new taskid
        console.log("Task submitted. Task ID:", taskId);
      } else {
        console.log(`Using provided Task ID: ${taskId}`);
        //someone might copy the entire line and in such case it is mitigated
        if (taskId.includes("Your unique code: ")) {
          taskId = taskId.replace("Your unique code: ", "");
          console.log(`Updated taskId: "${taskId.trim()}"`); // Output the updated string
        }
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
  const formatRemovalReasons = (removalReasons) => {
    console.log("Input to formatRemovalReasons:", removalReasons); // Log input

    const formattedReasons = Object.entries(removalReasons)
      .map(([fileName, reasons]) => {
        const reasonsList = reasons.map((reason) => `- ${reason}`).join("\n");
        return `${fileName}\n${reasonsList}`;
      })
      .join("\n\n");

    console.log("Output of formatRemovalReasons:", formattedReasons); // Log output
    return formattedReasons;
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

      const { status, message, removalReasons } = statusData;

      if (status === "FAILED") {
        console.error("FAILED:", message);
        console.error("FAILED RESASONS:", removalReasons);
        console.error("Task failed:", message);
        const reasons = formatRemovalReasons(removalReasons);
        console.log("Formatted reasons before setting state:", reasons);
        setRemovalReasondisp(reasons);
        setResponse({
          error: message || "Task failed with no additional message.",
        });
        console.log(`FAILED: "${message}"`);
        setServerError(message);
        setIsLoading(false);

        return;
      }

      if (status === "COMPLETED") {
        console.log("Task completed successfully!");
        const reasons = formatRemovalReasons(removalReasons);
        console.log("Formatted reasons before setting state:", reasons);
        setRemovalReasondisp(reasons);
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

  //koniec sendingu
  const [text, setText] = useState("");

  const handleTextChange = (newText) => {
    setText(newText); // Update the text state
  };

  const [isChecked, setIsChecked] = useState(false);

  const handleCheckChange = (checked) => {
    setIsChecked(checked);
  };
  const [sliderValue, setSliderValue] = useState(0.5);

  const handleSliderChange = (value) => {
    setSliderValue(value);
  };
  const [uploadedFiles, setUploadedFiles] = useState([]);

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

  const loadExampleFiles = async () => {
    try {
      // List of example files
      const exampleFiles = ["M6_git.pdb", "M6.pdb"];

      // Fetch each file from the public folder
      const files = await Promise.all(
        exampleFiles.map(async (fileName) => {
          const response = await fetch(`/examples/${fileName}`);
          const blob = await response.blob();
          return new File([blob], fileName, { type: blob.type });
        })
      );

      // Convert fetched files into dropzone-compatible format
      const newFiles = files.map((file, index) => ({
        id: Date.now() + index,
        file,
      }));

      setUploadedFiles(newFiles); // Replace current files with examples
    } catch (error) {
      console.error("Error loading example files:", error);
    }
  };

  const previewExampleFiles = () => {
    const exampleFiles = ["M6_git.pdb", "M6.pdb"]; //TODO, najlepiej jakby to był zip z plikami przykładowymi, bo przeglądarka się oburza na kilka pobrań naraz

    exampleFiles.forEach(async (fileName) => {
      try {
        const response = await fetch(`/examples/${fileName}`);
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = fileName;
        link.click();
        URL.revokeObjectURL(url);
      } catch (error) {
        console.error(`Error downloading ${fileName}:`, error);
      }
    });
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({ onDrop });

  const consensuses = ["Canonical", "Non-canonical", "Stacking", "All"]; //CANONICAL (domyślny), NON_CANONICAL , STACKING, ALL

  const [selectedConsensus, setSelectedOption] = useState(consensuses[0]);

  const handleSelect = (value) => {
    setSelectedOption(value); // Update the selected option
  };

  // const options = ['Option 1', 'Option 2', 'Option 3', 'Option 4'];

  // const [selectedConsensus, setSelectedOption] = useState(consensuses[0]); // Initial selected option

  // const handleSelect = (option) => {
  //   setSelectedOption(option);
  // };

  const annotators = [
    "MC-Annotate",
    "BARNABA",
    "RNAview",
    "FR3D",
    "BPnet",
    "RNApolis",
  ]; //CANONICAL (domyślny), NON_CANONICAL , STACKING, ALL
  const [selectedAnnotator, setSelectedOption2] = useState(annotators[0]); // Initial selected option

  const handleSelect2 = (option2) => {
    setSelectedOption2(option2);
  };

  const visulalisators = ["VARNA", "RNApuzzler", "PseudoViewer", "R-Chie"];
  const [selectedVisualisator, setSelectedOption3] = useState(
    visulalisators[0]
  ); // Initial selected option

  const handleSelect3 = (option2) => {
    setSelectedOption3(option2);
  };

  const molprobity = ["GOOD_AND_CAUTION", "GOOD_ONLY", "ALL"];
  const [selectedMolprobity, setSelectedOption4] = useState(molprobity[0]); // Initial selected option

  const handleSelect4 = (option2) => {
    setSelectedOption4(option2);
  };

  useEffect(() => {
    if (id) {
      console.log("Page loaded with ID:", id);
      handleSendData([], id);
      // Perform actions with the ID if necessary (e.g., fetch data based on the ID)
    }
  }, [id]);
  const formatBracketTxt = (text) => {
    // Split the text by "\n" and return an array of JSX elements
    return text.split("\n").map((line, index) => (
      <React.Fragment key={index}>
        {line}
        <br />
      </React.Fragment>
    ));
  };

  const renderContent = () => {
    if (isLoading) {
      return (
        <header className="App-header">
          <div class="rounded-border">
            <div className="spinner-container">
              <div className="spinner"></div>

              {id ? (
                <div>
                  <p>Waiting for completion...</p>
                  <p>Your task ID: {id}</p>
                </div>
              ) : (
                <p>Sending data...</p>
              )}
            </div>
          </div>
        </header>
      );
    }

    if (serverError) {
      return (
        <header className="App-header">
          <div class="rounded-border">
            <div className="error-container">
              <h2>Error</h2>
              <p>{serverError}</p>
              {removalReasondisp != null ? (
                <pre class="small-txt">{removalReasondisp}</pre>
              ) : (
                <p></p>
              )}
              <button
                className="reset-button"
                onClick={() => {
                  setServerError(null);
                  setIsLoading(false);
                  setResponse(null);
                  navigate(`/`, { replace: true }); //navigate to no taskid
                }}
              >
                Retry
              </button>
            </div>
          </div>
        </header>
      );
    }

    if (response) {
      return (
        <header className="App-header">
          <div class="rounded-border">
            <div className="response-container">
              <div className="center-items">
                <h2>Server Response</h2>
              </div>
              <div className="center-items-normal-txt">
                <p>Your unique code: {taskIdComplete}</p>
              </div>
              <div className="center-items-normal-txt">
                <p>Visualization:</p>
              </div>

              <div className="center-items">
                <SvgImg serverAddress={serverAddress} taskId={taskIdComplete} />
              </div>
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
                <p>Ranking:</p>
              </div>
              <ResultTable ranking={response.ranking} />

              <div className="center-items-normal-txt">
                <p>Dot bracket:</p>
              </div>
              <p className="small-txt-bracket">
                {formatBracketTxt(response.dotBracket)}
              </p>

              <div className="center-items-normal-txt">
                <p>Results for each file:</p>
              </div>
              {response.fileNames.map((filename, index) => (
                <FileDetails
                  key={index}
                  taskId={taskIdComplete}
                  serverAddress={serverAddress}
                  filename={filename}
                />
              ))}

              {removalReasondisp != null || removalReasondisp != "" ? (
                <div className="small-txt">
                  <p>Files removed by MolProbity:</p>
                  <pre class="small-txt">{removalReasondisp}</pre>
                </div>
              ) : (
                <p></p>
              )}

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
              <button
                className="reset-button"
                onClick={() => {
                  navigate(`/`, { replace: true }); //navigate to no taskid
                  setResponse(null);
                }}
              >
                Reset
              </button>
            </div>
          </div>
        </header>
      );
    }

    // Default view with the send button
    return (
      <div>
        <header className="App-header">
          <div class="rounded-border">
            <button className="send-button" onClick={loadExampleFiles}>
              Load Example
            </button>
            <button className="send-button" onClick={previewExampleFiles}>
              Preview Example Files
            </button>
            {/* <p
              style={{
                fontSize:
                  "25px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <b>Drop pdb files here</b>
            </p> */}
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

            <div>
              <div className="dropdown-text-container">
                <p className="dropdown-text">
                  <b>Pick consensus mode</b>
                </p>
                <Dropdown
                  options={consensuses}
                  value={selectedConsensus}
                  onSelect={handleSelect}
                />
                <p className="dropdown-text">
                  <b>Pick annotator</b>
                </p>
                <Dropdown
                  options={annotators}
                  value={selectedAnnotator}
                  onSelect={handleSelect2}
                />
              </div>

              {/* <p>Currently selected option: {selectedOption}</p> */}
              {/* <div className="dropdown-text-container">
                <p className="dropdown-text">
                  <b>Pick annotator</b>
                </p>
                <Dropdown
                  options={annotators}
                  value={selectedAnnotator}
                  onSelect={handleSelect2}
                />
              </div> */}
              <div className="dropdown-text-container">
                <p className="dropdown-text">
                  <b>Pick visulalisator</b>
                </p>
                <Dropdown
                  options={visulalisators}
                  value={selectedVisualisator}
                  onSelect={handleSelect3}
                />
                <p className="dropdown-text">
                  <b>Filter with mol probity?</b>
                </p>
                <Dropdown
                  options={molprobity}
                  value={selectedMolprobity}
                  onSelect={handleSelect4}
                />
              </div>

              {/* <div className="dropdown-text-container">
                <p className="dropdown-text">
                  <b>Filter with mol probity?</b>
                </p>
                <Dropdown
                  options={molprobity}
                  value={selectedMolprobity}
                  onSelect={handleSelect4}
                />
              </div> */}
              <div className="dropdown-text-container">
                <p className="dropdown-text">
                  <b>Pick confidence level</b>
                </p>
                <Slider
                  value={sliderValue}
                  onValueChange={handleSliderChange}
                />
                <p className="dropdown-text">
                  {sliderValue === 0 ? "Fuzzy mode: on" : "Fuzzy mode: off"}
                </p>
              </div>

              {/* <p>Current Slider Value: {sliderValue.toFixed(2)}</p> */}
              {/* <p>Currently selected option: {selectedOption}</p> */}
              {/* <div className="dropdown-text-container">
                <p className="dropdown-text">
                  <b>Filter with mol probity? </b>
                </p>
                <Checkbox
                  checked={isChecked}
                  onCheckChange={handleCheckChange}
                />
                <p
                  style={{
                    fontSize:
                      "18px" ,
                  }}
                >
                  {" "}
                  {isChecked ? "Yes" : "No"}
                </p>
              </div> */}

              <div className="dropdown-text-container">
                <p className="dropdown-text">
                  <b>Dot bracket parameter: </b>
                </p>
                <TextInputWide value={text} onTextChange={handleTextChange} />
              </div>
            </div>
            <div style={{ marginBottom: "60px" }}></div>
            <div className="center-items">
              <button
                className="send-button"
                onClick={
                  () =>
                    handleSendData([
                      selectedAnnotator,
                      selectedVisualisator,
                      selectedConsensus,
                      sliderValue.toFixed(2),
                      // isChecked,
                      selectedMolprobity,
                      //"GOOD_AND_CAUTION",
                      text,
                    ])
                  // handleSendData([
                  //   "BPNET",
                  //   "VARNA",
                  //   "CANONICAL",
                  //   0.5,
                  //   // isChecked,
                  //   "GOOD_AND_CAUTION",
                  //   "",
                  // ])
                }
              >
                Send Data
              </button>
            </div>
            <div className="center-items">
              <p
                style={{
                  fontSize:
                    "18px" /* This font size is set using a 'string value' */,
                }}
              >
                or get already calculated results:
              </p>
            </div>
            <div className="center-items">
              <TextInput value={text2} onTextChange={handleTextChange2} />
            </div>
            <div className="center-items">
              <button
                className="send-button"
                onClick={() => handleSendData([], text2)}
              >
                Get results
              </button>
            </div>
          </div>
        </header>
      </div>
    );
  };
  return <div>{renderContent()}</div>;
  // return (
  //   <div>
  //     <header className="App-header">
  //       <div class="rounded-border">
  //         <p
  //           style={{
  //             fontSize:
  //               "25px" /* This font size is set using a 'string value' */,
  //             display: "flex",
  //             alignItems: "center",
  //             justifyContent: "center",
  //           }}
  //         >
  //           <b>Drop pdb files here</b>
  //         </p>
  //         <div className="home-container">
  //           <div className="dropzone-container" {...getRootProps()}>
  //             <input {...getInputProps()} />
  //             {isDragActive ? (
  //               <p>Drop the files here ...</p>
  //             ) : (
  //               <p>Drag & drop files here, or click to select files</p>
  //             )}
  //           </div>

  //           <div className="file-list">
  //             {uploadedFiles.map((fileWrapper) => (
  //               <div key={fileWrapper.id} className="file-item">
  //                 <span
  //                   style={{
  //                     fontSize:
  //                       "18px" /* This font size is set using a 'string value' */,
  //                     display: "flex",
  //                     alignItems: "center",
  //                     justifyContent: "center",
  //                   }}
  //                 >
  //                   {fileWrapper.file.name}
  //                 </span>
  //                 <button
  //                   className="delete-button"
  //                   onClick={() => removeFile(fileWrapper.id)}
  //                 >
  //                   X
  //                 </button>
  //               </div>
  //             ))}
  //           </div>
  //         </div>

  //         <div>
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Pick consensus mode</b>
  //             </p>
  //             <Dropdown
  //               options={consensuses}
  //               value={selectedConsensus}
  //               onSelect={handleSelect}
  //             />
  //           </div>

  //           {/* <p>Currently selected option: {selectedOption}</p> */}
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Pick annotator</b>
  //             </p>
  //             <Dropdown
  //               options={annotators}
  //               value={selectedAnnotator}
  //               onSelect={handleSelect2}
  //             />
  //           </div>
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Pick visulalisator</b>
  //             </p>
  //             <Dropdown
  //               options={visulalisators}
  //               value={selectedVisualisator}
  //               onSelect={handleSelect3}
  //             />
  //           </div>
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Pick confidence level</b>
  //             </p>
  //             <Slider value={sliderValue} onValueChange={handleSliderChange} />
  //           </div>

  //           {/* <p>Current Slider Value: {sliderValue.toFixed(2)}</p> */}
  //           {/* <p>Currently selected option: {selectedOption}</p> */}
  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Filter with mol probity? </b>
  //             </p>
  //             <Checkbox checked={isChecked} onCheckChange={handleCheckChange} />
  //             <p
  //               style={{
  //                 fontSize:
  //                   "18px" /* This font size is set using a 'string value' */,
  //               }}
  //             >
  //               {" "}
  //               {isChecked ? "Yes" : "No"}
  //             </p>
  //           </div>

  //           <div className="dropdown-text-container">
  //             <p className="dropdown-text">
  //               <b>Dot bracket parameter: </b>
  //             </p>
  //             <TextInput value={text} onTextChange={handleTextChange} />
  //           </div>
  //         </div>
  //       </div>
  //     </header>
  //   </div>
  // );
}

export default Home;
