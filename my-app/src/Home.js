import React, { useEffect, useState, useCallback } from "react";

import { useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Slider,
  Spin,
  Switch,
  Table,
  Tabs,
  Tooltip,
  Upload,
  Typography,
  message,
} from "antd";
import { getTableColumns, getTableRows } from "./utils/tableUtils";
import {
  QuestionCircleOutlined,
  CloseOutlined,
  UploadOutlined,
} from "@ant-design/icons";

import SvgImg from "./SvgImg";
import FileDetails from "./FileDetails";
import * as configs from "./config";
import DownloadButton from "./DownloadButton";
import "./customTextInput.css";

const { Text } = Typography;
const { TextArea } = Input;

const analyzerOptions = [
  {
    value: "RNAPOLIS",
    label: "RNApolis Annotator",
  },
  {
    value: "BPNET",
    label: "BPNet",
  },
  {
    value: "FR3D",
    label: "FR3D",
  },
  {
    value: "MCANNOTATE",
    label: "MC-Annotate",
  },
  {
    value: "RNAVIEW",
    label: "RNAView",
  },
  {
    value: "BARNABA",
    label: "Barnaba",
  },
];
const molProbityOptions = [
  {
    value: "ALL",
    label: "No filter (keep all models)",
  },
  {
    value: "CLASHSCORE",
    label: "Clashscore filter (remove models with poor clashscore)",
  },
  {
    value: "CLASHSCORE_BONDS_ANGLES",
    label:
      "Strict filter (remove models with poor clashscore, bond, or angle geometry)",
  },
];

function Home() {
  const serverAddress = configs.default.SERVER_ADDRESS;
  const { id } = useParams();
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(false);
  const [response, setResponse] = useState(null);
  const [removalReasons, setRemovalReasons] = useState(null);
  const [serverError, setServerError] = useState(null);
  const [taskIdComplete, setTaskIdComplete] = useState(null);
  const [fileList, setFileList] = useState([]);
  const [analyzer, setAnalyzer] = useState(analyzerOptions[0].value);
  const [molProbityFilter, setMolProbityFilter] = useState(
    molProbityOptions[0].value
  );
  const [isFuzzy, setIsFuzzy] = useState(true);
  const [confidenceLevel, setConfidenceLevel] = useState(fileList.length);
  const [dotBracket, setDotBracket] = useState(null);

  const [sequenceToCheck, setSequenceToCheck] = useState("");
  const [isSequenceOk, setIsSequenceOk] = useState(true);
  const [isDotBracketOk, setIsDotBracketOk] = useState(true);
  const [sequenceList, setSequenceList] = useState([]);
  const [seqLength, setSeqLength] = useState(0);
  const [brackLength, setBrackLength] = useState(0);
  const [opBracketsNum, setOpBracketsNum] = useState(0);
  const [clBracketsNum, setClBracketsNum] = useState(0);
  const addOrUpdateSequenceList = (name, sequence) => {
    console.log("added or updated file", name);
    console.log("=====================", sequence);
    setSequenceList((prev) =>
      prev.some((item) => item.name === name)
        ? prev.map((item) =>
            item.name === name ? { ...item, sequence } : item
          )
        : [...prev, { name, sequence }]
    );
  };
  const removeFromSequenceList = (name) => {
    setSequenceList((prev) => prev.filter((item) => item.name !== name));
  };
  const findSequenceInSequenceList_local = (list, sequence) => {
    return list
      .filter((item) => item.sequence === sequence)
      .map((item) => item.name);
  };
  const resetFileList = () => {
    //resets both file list and dotBracket textview
    setDotBracket("");
    setFileList([]);
    setSequenceToCheck("");
    setIsSequenceOk(true);
    setIsDotBracketOk(true);
  };
  const resetDotBracket = () => {
    //resets the dotBracket textview
    setDotBracket("");
    setSeqLength(0);
    setBrackLength(0);
    setOpBracketsNum(0);
    setClBracketsNum(0);
  };
  // Ensure confidenceLevel is within valid range when fileList updates
  useEffect(() => {
    setConfidenceLevel((prev) => Math.min(prev, fileList.length) || 2);
  }, [fileList.length]);
  const handleSliderChange = (value) => {
    if (value <= fileList.length) {
      setConfidenceLevel(value);
    }
  };
  const beforeUpload = async (file) => {
    file.url = URL.createObjectURL(file);
    file.obj = new File([file], file.name, { type: file.type });

    // Check if file is an archive
    const isArchive =
      file.name.toLowerCase().endsWith(".zip") ||
      file.name.toLowerCase().endsWith(".tar.gz") ||
      file.name.toLowerCase().endsWith(".tgz");

    // Add the file to the list with a processing status
    const fileWithUid = {
      uid: `${Date.now()}_${file.name}`,
      name: file.name,
      status: "uploading",
      percent: 0,
      url: file.url,
      obj: file.obj,
      isArchive: isArchive,
    };

    setFileList((prevFileList) => [...prevFileList, fileWithUid]);

    try {
      // Create a FormData object to send the file
      const formData = new FormData();
      formData.append("file", file);

      // Update the status message for archives
      if (fileWithUid.isArchive) {
        setFileList((prevFileList) =>
          prevFileList.map((f) =>
            f.uid === fileWithUid.uid
              ? {
                  ...f,
                  status: "uploading",
                  percent: 10,
                  name: `${file.name} (Extracting archive...)`,
                }
              : f
          )
        );
      }

      // Call the split endpoint
      const response = await fetch(`${serverAddress}/split`, {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`Server responded with status ${response.status}`);
      }

      const result = await response.json();
      console.log("RESPONSE:", result);
      console.log("SEQUENCE:", result.sequence);

      // If we got split files back
      if (result.files && result.files.length > 0) {
        // If there's more than one file returned, it means the file was split
        // or it was an archive that was extracted
        let localSequenceToCheck = sequenceToCheck;
        result.files.forEach((file, index) => {
          //setSequenceToCheck("");
          //setIsSequenceOk(true);
          //setIsDotBracketOk(true);
          console.log(`File:`, file);
          console.log(`File:`, file.sequence);
          if (
            localSequenceToCheck === "" &&
            file.sequence !== "" &&
            file.sequence != null
          ) {
            localSequenceToCheck = file.sequence;
            setSequenceToCheck(file.sequence);
            checkDotBracket(dotBracket, file.sequence);
          }
          if (localSequenceToCheck === file.sequence) {
            console.log(`File ${index}: seq is ok sequence =`, file.sequence);
            console.log(`Sequence to check =`, localSequenceToCheck);
          } else {
            setIsSequenceOk(false);
            console.log(`File ${index}: seq not ok sequence =`, file.sequence);
            console.log(`Sequence to check =`, localSequenceToCheck);
          }
        });

        if (
          result.files.length > 1 ||
          fileWithUid.isArchive ||
          result.files[0].name !== file.name
        ) {
          // Sort files by model number if they follow the pattern base_model_n.ext
          const sortedFiles = [...result.files].sort((a, b) => {
            const modelNumberA = a.name.match(/_model_(\d+)\./);
            const modelNumberB = b.name.match(/_model_(\d+)\./);

            if (modelNumberA && modelNumberB) {
              return parseInt(modelNumberA[1]) - parseInt(modelNumberB[1]);
            }
            return a.name.localeCompare(b.name); // Fallback to alphabetical sort
          });

          // Create File objects for each split file
          const splitFiles = sortedFiles.map((fileData, index) => {
            const newFileName = fileData.name || `split_${index}_${file.name}`;
            const newFile = new File([fileData.content], newFileName, {
              type: file.type,
            });
            newFile.url = URL.createObjectURL(newFile);
            newFile.obj = newFile;

            //also save the information on sequenceList
            addOrUpdateSequenceList(newFileName, fileData.sequence);

            return {
              uid: `${Date.now()}_${index}`,
              name: newFile.name,
              status: "done",
              url: newFile.url,
              obj: newFile,
            };
          });

          // Update the file list with the split files
          setFileList((prevFileList) => {
            // Remove the original file (the one with uploading status)
            const filteredList = prevFileList.filter(
              (f) => f.uid !== fileWithUid.uid
            );
            // Add the split files
            const newList = [...filteredList, ...splitFiles];

            // Log the result for debugging
            if (fileWithUid.isArchive) {
              console.log(
                `Archive ${file.name} extracted into ${splitFiles.length} files`
              );
            } else {
              console.log(
                `File ${file.name} split into ${splitFiles.length} files`
              );
            }

            return newList;
          });

          // Clean up the original file URL
          URL.revokeObjectURL(file.url);

          // Return false to prevent the default antd upload behavior
          return false;
        } else {
          // Only one file returned, update the status of the existing file
          setFileList((prevFileList) =>
            prevFileList.map((f) =>
              f.uid === fileWithUid.uid ? { ...f, status: "done" } : f
            )
          );
          //also add the file to the sequenceList
          //also save the information on sequenceList
          addOrUpdateSequenceList(file.name, result.files[0].sequence);
        }
      } else {
        // No files returned, update the status of the existing file
        setFileList((prevFileList) =>
          prevFileList.map((f) =>
            f.uid === fileWithUid.uid ? { ...f, status: "done" } : f
          )
        );
      }
    } catch (error) {
      console.error("Error processing file:", error);
      // If there's an error, update the file status to error with appropriate message
      const errorMessage = fileWithUid.isArchive
        ? `Error extracting archive: ${error.message}`
        : `Error splitting file: ${error.message}`;

      setFileList((prevFileList) =>
        prevFileList.map((f) =>
          f.uid === fileWithUid.uid
            ? { ...f, status: "error", error: errorMessage, name: file.name }
            : f
        )
      );
    }

    // Return false to prevent the default antd upload behavior in all cases
    return false;
  };

  const handleDownload = (file) => {
    const link = document.createElement("a");
    link.href = file.url;
    link.download = file.name;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handleFileListChange = ({ fileList: newFileList }) => {
    var removedFiles = [];
    // Clean up removed file URLs
    fileList.forEach((file) => {
      if (!newFileList.find((f) => f.uid === file.uid)) {
        URL.revokeObjectURL(file.url);
        removedFiles.push(file.name);
      }
    });
    console.log("removed files", removedFiles);
    // We'll let the beforeUpload function handle the file list updates
    // This is mainly for handling file removals
    const hasRemovals = fileList.length > newFileList.length;
    if (hasRemovals) {
      setFileList(newFileList);
      if (newFileList.length === 0) {
        resetFileList();
      } else {
        //we also check if we removed all problematic files
        //(unmatching the reference sequence, or if we are supposed to change the reference sequence)
        //remove the file we just removed
        var sequenceListLocal = sequenceList;
        removedFiles.forEach((f) => {
          removeFromSequenceList(f);
          sequenceListLocal = sequenceListLocal.filter(
            (item) => item.name !== f
          );
        });
        //check how many files of required sequence remain
        var filesInSlist = findSequenceInSequenceList_local(
          sequenceListLocal,
          sequenceToCheck
        );

        //check if there are also examples on the file list
        if (sequenceListLocal.length < newFileList.length) {
          //there are examples, so we force stay with the example sequence as the one to check
          //check, if there still are files not fitting the required sequence
          if (filesInSlist.length < sequenceListLocal.length) {
            //there certainly are such files, value remains false
          } else {
            //all files caught as correct, we set to true
            setIsSequenceOk(true);
          }
        } else {
          //there aren't any example files, reevaluate, if the list is mayhaps supposed to have new sequence to check
          //check, if there still are files not fitting the required sequence
          if (filesInSlist.length < sequenceListLocal.length) {
            //there certainly are such files
            //check if no file of old sequence remains:
            if (filesInSlist.length === 0) {
              setSequenceToCheck(sequenceListLocal[0].sequence);
              checkDotBracket(dotBracket, sequenceListLocal[0].sequence);
              //check if only this sequence is within the list, if so, sequence of files is fine
              var filesInSlistNew = findSequenceInSequenceList_local(
                sequenceListLocal,
                sequenceListLocal[0].sequence
              );
              if (filesInSlistNew.length === sequenceListLocal.length) {
                setIsSequenceOk(true);
              }
            }
          } else {
            //all files caught as correct, we set to true
            setIsSequenceOk(true);
          }
        }
      }
    }
  };

  // Clean up on unmount
  useEffect(() => {
    return () => {
      fileList.forEach((file) => {
        URL.revokeObjectURL(file.url);
      });
    };
  }, [fileList]);

  const checkDotBracket = (string, seqToCheck) => {
    if (string === "" || string === null) {
      setIsDotBracketOk(true);
      return;
    }
    /*
     * Regex:
     * (>.+\r?\n)?([ACGUTRYNacgutryn]+)\r?\n([-.()\[\]{}<>A-Za-z]+)
     *
     * Groups:
     *  1: strand name with leading '>' or null
     *  2: sequence
     *  3: structure
     */
    const regex =
      /(>.+\r?\n)?([ACGUTRYNacgutryn]+)\r?\n([-.()\[\]{}<>A-Za-z]+)/g;
    const matches = [...string.matchAll(regex)];
    const text = string;
    const extractedSequence = matches.map((m) => m[2]).join("");
    const extractedBrackets = matches.map((m) => m[3]).join("");
    const openRegex = /[([{<]/g;
    const openings = extractedBrackets.match(openRegex) || [];
    console.log("Opening brackets:", openings);
    console.log("Count:", openings.length);
    const closeRegex = /[)\]}>]/g;
    const closings = extractedBrackets.match(closeRegex) || [];
    console.log("Closing brackets:", closings);
    console.log("Count:", closings.length);
    setClBracketsNum(closings.length);
    setOpBracketsNum(openings.length);
    //const match = text.match(regex);
    if (text === "") {
      setIsDotBracketOk(true);
    } else {
      if (matches) {
        if (extractedSequence === seqToCheck) {
          console.log("✅ Sequence matches");
          setIsDotBracketOk(true);
        } else {
          console.log("❌ Sequence does not match");
          setIsDotBracketOk(false);
        }
        setSeqLength(extractedSequence.length);
        setBrackLength(extractedBrackets.length);
      } else {
        console.log("No match found in input.");
        setIsDotBracketOk(false);
      }
    }
  };
  const handleDotBracket = (event) => {
    checkDotBracket(event.target.value, sequenceToCheck);
    setDotBracket(event.target.value);
  };

  const fetchTaskResult = useCallback(async (taskId, setResponse) => {
    try {
      // Fetch main results
      const resultResponse = await fetch(`${serverAddress}/${taskId}/result`, {
        method: "GET",
      });
      if (!resultResponse.ok) {
        throw new Error(
            `Failed to get result. Status: ${resultResponse.status}`
        );
      }
      const resultData = await resultResponse.json();

      // Fetch user request parameters
      const requestResponse = await fetch(
          `${serverAddress}/${taskId}/request`,
          {
            method: "GET",
          }
      );
      if (!requestResponse.ok) {
        throw new Error(
            `Failed to get request parameters. Status: ${requestResponse.status}`
        );
      }
      const requestData = await requestResponse.json();

      // Fetch molProbity response
      const molProbityResponse = await fetch(
          `${serverAddress}/${taskId}/molprobity`,
          {
            method: "GET",
          }
      );
      if (!molProbityResponse.ok) {
        throw new Error(
            `Failed to get molProbity. Status: ${molProbityResponse.status}`
        );
      }
      const molProbityData = await molProbityResponse.json();
      console.log("Mol Probity data: ", molProbityData);
      // Combine results and request parameters
      const combinedData = {
        ...resultData,
        userRequest: requestData, // Add the fetched request data
        molProbity: molProbityData, // Add the fetched request data
      };

      // Set the response state with combined data
      setResponse(combinedData);
    } catch (error) {
      console.error("Error fetching task data:", error.message);
      setResponse({ error: error.message }); // Set error state
    } finally {
      setIsLoading(false);
      setTaskIdComplete(taskId);
    }
  }, [serverAddress, setIsLoading, setTaskIdComplete /* setResponse is passed as arg */]);

  const pollTaskStatus = useCallback(async (taskId, pollInterval, setResponse) => {
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
      const { status, message, removalReasons } = statusData;
      setRemovalReasons(removalReasons);

      if (status === "FAILED") {
        console.error("FAILED:", message);
        console.error("FAILED REASONS:", removalReasons);
        setResponse({
          error: message || "Task failed with no additional message.",
        });
        setServerError(message);
        setIsLoading(false);
        return;
      }

      if (status === "COMPLETED") {
        // Pass setResponse to fetchTaskResult if it's not already part of its closure
        // or if fetchTaskResult is not using a local setResponse from its own scope.
        // In this case, fetchTaskResult takes setResponse as an argument.
        await fetchTaskResult(taskId, setResponse);
        return;
      }

      // If still processing, wait and try again
      await new Promise((resolve) => setTimeout(resolve, pollInterval));
    }
  }, [serverAddress, fetchTaskResult, setRemovalReasons, setIsLoading, setServerError /* setResponse is passed as arg */]);

  const handleSendData = useCallback(async (taskIdArg = "") => {
    const POLL_INTERVAL = 3000; // 3 seconds
    let taskId = taskIdArg || null;

    try {
      // Step 1: Create and send the payload if taskId is not provided
      if (!taskId) {
        setIsLoading(true);
        setResponse(null);
        setServerError(null);
        setRemovalReasons(null);

        // Prepare the files data
        const files = await Promise.all(
          fileList.map(async (file) => ({
            name: file.obj.name,
            content: await file.obj.text(), // Reads file content as text
          }))
        );

        // Prepare the payload
        const payload = {};
        payload.files = files;
        payload.analyzer = analyzer;
        if (isFuzzy) {
          payload.confidenceLevel = null;
        } else {
          payload.confidenceLevel = confidenceLevel;
        }
        payload.molProbityFilter = molProbityFilter;
        if (dotBracket) {
          payload.dotBracket = dotBracket;
        }

        const response = await fetch(serverAddress, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(payload),
        });

        if (!response.ok) {
          if (response.status === 413) {
            throw new Error(
              "Request too large. Please reduce the size or number of files."
            );
          }
          throw new Error(`Server responded with status ${response.status}`);
        }

        const { taskId: newTaskId } = await response.json();
        taskId = newTaskId;
        navigate(`/${newTaskId}`, { replace: true }); //navigate to new taskid
      }

      // Step 2: Poll for task status
      // Step 2: Poll for task status.
      // pollTaskStatus will internally call fetchTaskResult when status is COMPLETED.
      await pollTaskStatus(taskId, POLL_INTERVAL, setResponse);

      // Step 3: Handle the result based on task status is now managed within pollTaskStatus
      // and fetchTaskResult, so no further action is needed here for COMPLETED status.
    } catch (error) {
      console.error("Error in submission process:", error.message);
      if (taskId) {
        console.error(`Task ID: ${taskId}`);
      }
      setServerError(error.message);
      setIsLoading(false);
    }
  }, [
    serverAddress,
    navigate,
    fileList,
    analyzer,
    isFuzzy,
    confidenceLevel,
    molProbityFilter,
    dotBracket,
    pollTaskStatus,
    fetchTaskResult,
    setIsLoading,
    setResponse,
    setServerError,
    setRemovalReasons,
  ]);

  // Perform actions with the ID if necessary (e.g., fetch data based on the ID)
  useEffect(() => {
    if (id) {
      handleSendData(id);
    }
  }, [handleSendData, id]);

  const loadRNAPuzzlesExample = async () => {
    try {
      // List of example files
      const exampleFiles = [
        "1_bujnicki_1_rpr.pdb",
        "1_bujnicki_2_rpr.pdb",
        "1_bujnicki_3_rpr.pdb",
        "1_bujnicki_4_rpr.pdb",
        "1_bujnicki_5_rpr.pdb",
        "1_chen_1_rpr.pdb",
        "1_das_1_rpr.pdb",
        "1_das_2_rpr.pdb",
        "1_das_3_rpr.pdb",
        "1_das_4_rpr.pdb",
        "1_das_5_rpr.pdb",
        "1_dokholyan_1_rpr.pdb",
        "1_major_1_rpr.pdb",
      ];

      // Fetch each file from the public folder
      const files = await Promise.all(
        exampleFiles.map(async (fileName) => {
          const response = await fetch(`/examples/${fileName}`);
          const blob = await response.blob();
          return new File([blob], fileName, { type: blob.type });
        })
      );

      // Convert fetched files into compatible format
      const newFiles = files.map((file, index) => ({
        uid: Date.now() + index,
        name: file.name,
        status: "done",
        url: URL.createObjectURL(file),
        originFileObj: file,
        obj: file,
      }));
      resetFileList();
      setSequenceToCheck("CCGCCGCGCCAUGCCUGUGGCGGCCGCCGCGCCAUGCCUGUGGCGG");
      setFileList(newFiles); // Replace current files with examples
      setDotBracket(
        ">strand_A\n" +
          "CCGCCGCGCCAUGCCUGUGGCGG\n" +
          "((((((((.((((...(((((((\n" +
          ">strand_B\n" +
          "CCGCCGCGCCAUGCCUGUGGCGG\n" +
          ")))))))..)))).).)))))))"
      );
    } catch (error) {
      console.error("Error loading example files:", error);
    }
  };

  const loadDecoyExamples = async () => {
    try {
      // List of example files
      const exampleFiles = [
        "1a9nR_M1.pdb",
        "1a9nR_M2.pdb",
        "1a9nR_M3.pdb",
        "1a9nR_M4.pdb",
        "1a9nR_M5.pdb",
        "1a9nR_M6.pdb",
        "1a9nR_M7.pdb",
        "1a9nR_M8.pdb",
        "1a9nR_M9.pdb",
      ];

      // Fetch each file from the public folder
      const files = await Promise.all(
        exampleFiles.map(async (fileName) => {
          const response = await fetch(`/examples/${fileName}`);
          const blob = await response.blob();
          return new File([blob], fileName, { type: blob.type });
        })
      );

      // Convert fetched files into compatible format
      const newFiles = files.map((file, index) => ({
        uid: Date.now() + index,
        name: file.name,
        status: "done",
        url: URL.createObjectURL(file),
        originFileObj: file,
        obj: file,
      }));

      resetFileList();
      setSequenceToCheck("CCUGGUAUUGCAGUACCUCCAGGU");
      setFileList(newFiles); // Replace current files with examples
      setDotBracket(
        ">strand_R\n" +
          "CCUGGUAUUGCAGUACCUCCAGGU\n" +
          "(((((.............)))))."
      );
    } catch (error) {
      console.error("Error loading example files:", error);
    }
  };

  const loadmiRNAExample = async () => {
    try {
      // List of example files
      const exampleFiles = [
        "mir663_0001.pdb",
        "mir663_0002.pdb",
        "mir663_0003.pdb",
        "mir663_0004.pdb",
        "mir663_0005.pdb",
        "mir663_0006.pdb",
        "mir663_0007.pdb",
        "mir663_0008.pdb",
        "mir663_0009.pdb",
        "mir663_0010.pdb",
      ];

      // Fetch each file from the public folder
      const files = await Promise.all(
        exampleFiles.map(async (fileName) => {
          const response = await fetch(`/examples/${fileName}`);
          const blob = await response.blob();
          return new File([blob], fileName, { type: blob.type });
        })
      );

      // Convert fetched files into compatible format
      const newFiles = files.map((file, index) => ({
        uid: Date.now() + index,
        name: file.name,
        status: "done",
        url: URL.createObjectURL(file),
        originFileObj: file,
        obj: file,
      }));

      resetFileList();
      setSequenceToCheck(
        "CCUUCCGGCGUCCCAGGCGGGGCGCCGCGGGACCGCCCUCGUGUCUGUGGCGGUGGGAUCCCGCGGCCGUGUUUUCCUGGUGGCCCGGCC"
      );
      setFileList(newFiles); // Replace current files with examples
      setDotBracket(
        ">strand_A\n" +
          "CCUUCCGGCGUCCCAGGCGGGGCGCCGCGGGACCGCCCUCGUGUCUGUGGCGGUGGGAUCCCGCGGCCGUGUUUUCCUGGUGGCCCGGCC\n" +
          "....((((..(((((((.((.(((((((((((((((((........).))))).....))))))))).)).))..))))).)).)))).."
      );
    } catch (error) {
      console.error("Error loading example files:", error);
    }
  };

  const handleRemovalReasons = () => {
    let columns = [
      {
        title: "Filename",
        dataIndex: "filename",
        key: "filename",
      },
      {
        title: "Removal reason",
        dataIndex: "reason",
        key: "reason",
      },
    ];
    let rows = [];
    let i = 0;
    Object.entries(removalReasons).map(([filename, reasons]) => {
      reasons.map((reason) => {
        rows.push({ key: i, filename: filename, reason: reason });
        i++;
      });
    });
    return [columns, rows];
  };

  // Inside your component:
  const copyToClipboard = useCallback(() => {
    navigator.clipboard
      .writeText(id)
      .then(() => message.success("ID copied to clipboard!"))
      .catch(() => message.error("Failed to copy ID."));
  }, [id]);

  const renderContent = () => {
    if (isLoading) {
      if (id) {
        return (
          <Row
            justify="center"
            style={{
              minHeight: "50vh",
              alignItems: "center",
              flexDirection: "column",
              display: "flex",
              padding: 15,
            }}
          >
            <div style={{ marginBottom: 14, textAlign: "center" }}>
              <Text>
                Your task is being processed:{" "}
                <Text
                  underline
                  style={{ color: "#1890ff", cursor: "pointer" }}
                  onClick={copyToClipboard}
                >
                  {id}
                </Text>
                <br />
                Processing time may vary depending on dataset size.
              </Text>
            </div>
            <div style={{ marginBottom: 24, textAlign: "center" }}></div>
            <Spin
              size="large"
              spinning={true}
              style={{ transform: "scale(1.5)" }} // Increase spinner size
            >
              <div style={{ width: "100vw", height: 1 }} />
            </Spin>
          </Row>
        );
      }
      return (
        <Row justify={"center"}>
          <Spin
            spinning={true}
            style={{ transform: "scale(1.5)" }} // Increase spinner size
            tip={"Sending data to the server"}
            size="large"
          >
            <div style={{ width: "100vw", padding: 24 }} />
          </Spin>
        </Row>
      );
    }

    if (serverError) {
      if (removalReasons && Object.keys(removalReasons).length > 0) {
        const [columns, rows] = handleRemovalReasons();
        return (
          <Row justify={"center"} style={{ marginBottom: 24 }}>
            <Col span={20}>
              <Alert
                type="error"
                message="Error"
                description={
                  <div>
                    <p>{serverError}</p>
                    <Table
                      dataSource={rows}
                      columns={columns}
                      scroll={{ x: "max-content" }}
                    />
                  </div>
                }
              />
              <Button
                onClick={() => {
                  setServerError(null);
                  setIsLoading(false);
                  setResponse(null);
                  navigate(`/`, { replace: true }); //navigate to no taskid
                }}
              >
                Retry
              </Button>
            </Col>
          </Row>
        );
      }
      return (
        <Row justify={"center"}>
          <Col span={20}>
            <Alert
              type="error"
              message="Error"
              description={
                <div>
                  <p>{serverError}</p>
                </div>
              }
            />
            <Button
              onClick={() => {
                setServerError(null);
                setIsLoading(false);
                setResponse(null);
                navigate(`/`, { replace: true }); //navigate to no taskid
              }}
            >
              Retry
            </Button>
          </Col>
        </Row>
      );
    }

    if (response) {
      const totalFiles = response.fileNames.length;

      // --- Modifications for Ranking Table ---
      const rankingTableHeaders = response.ranking.headers;
      const rankingTableRowsData = response.ranking.rows;

      // Identify Rank column indices
      const rankColumnIndices = rankingTableHeaders
        .map((header, index) => (header.startsWith("Rank (") ? index : -1))
        .filter((index) => index !== -1);

      // Modify rows to make rank values integers
      const modifiedRankingRowsData = rankingTableRowsData.map((row) => {
        const newRow = [...row]; // Create a mutable copy
        rankColumnIndices.forEach((idx) => {
          if (
            newRow[idx] !== null &&
            newRow[idx] !== undefined &&
            !isNaN(parseFloat(newRow[idx]))
          ) {
            newRow[idx] = Math.round(parseFloat(newRow[idx]));
          }
        });
        return newRow;
      });

      // Generate ranking columns
      const rankingColumns = getTableColumns(
        rankingTableHeaders,
        modifiedRankingRowsData, // Use modified data for consistency if sorters depend on it
        totalFiles
      );

      // Set default sort order for "Rank (ALL)"
      const rankAllColumnConfig = rankingColumns.find(
        (col) => col.title === "Rank (ALL)"
      );
      if (rankAllColumnConfig) {
        rankAllColumnConfig.defaultSortOrder = "ascend";
        // Ensure sorter is present (getTableColumns should add it)
        if (!rankAllColumnConfig.sorter) {
           // This case should ideally not happen if getTableColumns is consistent
          rankAllColumnConfig.sorter = (a, b) => {
            const valA = a[rankAllColumnConfig.dataIndex];
            const valB = b[rankAllColumnConfig.dataIndex];
            if (!isNaN(valA) && !isNaN(valB)) {
              return valA - valB;
            }
            return String(valA).localeCompare(String(valB));
          };
        }
      }
      // --- End Modifications for Ranking Table ---

      const canonicalColumns = getTableColumns(
        response.canonicalPairs.headers,
        response.canonicalPairs.rows,
        totalFiles
      );
      const nonCanonicalColumns = getTableColumns(
        response.nonCanonicalPairs.headers,
        response.nonCanonicalPairs.rows,
        totalFiles
      );
      const stackingColumns = getTableColumns(
        response.stackings.headers,
        response.stackings.rows,
        totalFiles
      );
      const rankingRows = getTableRows(modifiedRankingRowsData); // Use modified data for rows
      const canonicalRows = getTableRows(response.canonicalPairs.rows);
      const nonCanonicalRows = getTableRows(response.nonCanonicalPairs.rows);
      const stackingRows = getTableRows(response.stackings.rows);

      const consensusDetails = [
        {
          key: "consensus-2d-structure",
          label: "Secondary structure",
          children: (
            <div>
              <Row gutter={16}>
                <Col span={12}>
                  <SvgImg
                    key="svg-varna-consensus"
                    serverAddress={serverAddress}
                    taskId={taskIdComplete}
                    svgName={"consensus"}
                  />
                </Col>
                <Col span={12}>
                  <SvgImg
                    key="svg-rchie-consensus"
                    serverAddress={serverAddress}
                    taskId={taskIdComplete}
                    svgName={"rchie-consensus"}
                  />
                </Col>
              </Row>
              <pre
                key="dotbracket"
                style={{
                  whiteSpace: "pre-wrap",
                  wordBreak: "break-word",
                  marginTop: "16px",
                }}
              >
                {response.dotBracket}
              </pre>
            </div>
          ),
        },
        {
          key: "consensus-base-pairs",
          label: "Canonical base pairs",
          children: (
            <>
              <Table
                dataSource={canonicalRows}
                columns={canonicalColumns}
                scroll={{ x: "max-content" }}
              />
              <DownloadButton
                dataSource={canonicalRows}
                columns={canonicalColumns}
                fileName={`consensus_base_pairs.txt`}
              />
            </>
          ),
        },
        {
          key: "consensus-non-canonical-pairs",
          label: "Non-canonical base pairs",
          children: (
            <>
              <Table
                dataSource={nonCanonicalRows}
                columns={nonCanonicalColumns}
                scroll={{ x: "max-content" }}
              />
              <DownloadButton
                dataSource={nonCanonicalRows}
                columns={nonCanonicalColumns}
                fileName={`consensus_non_canonical_pairs.txt`}
              />
            </>
          ),
        },
        {
          key: "consensus-stacking-interactions",
          label: "Stacking interactions",
          children: (
            <>
              <Table
                dataSource={stackingRows}
                columns={stackingColumns}
                scroll={{ x: "max-content" }}
              />
              <DownloadButton
                dataSource={stackingRows}
                columns={stackingColumns}
                fileName={`consensus_stacking_interactions.txt`}
              />
            </>
          ),
        },
      ];
      const perFileDetails = response.fileNames.map((filename, index) => ({
        key: index,
        label: filename,
        children: (
          <FileDetails
            taskId={taskIdComplete}
            serverAddress={serverAddress}
            filename={filename}
            fileCount={totalFiles}
          />
        ),
      }));

      const molProbityDetails = Object.entries(response.molProbity).map(
        ([key, fileData], index) => ({
          key: index,
          label: key,
          children: (
            <div>
              <h2>{fileData.structure.description.filename}</h2>

              <div>
                <b>Clashscore: </b> {fileData.structure.clashscore}
              </div>
              <div>
                <b>Bad angles: </b> {fileData.structure.pctBadAngles}%
              </div>
              <div>
                <b>Bad backbone conformations: </b>{" "}
                {fileData.structure.pctBadBackboneConformations}%
              </div>
              <div>
                <b>Bad bonds: </b> {fileData.structure.pctBadBonds}%
              </div>
              <div>
                <b>Probably Wrong Sugar Puckers: </b>{" "}
                {fileData.structure.pctProbablyWrongSugarPuckers}% (category:{" "}
                {fileData.structure.probablyWrongSugarPuckersCategory ===
                  "Good" && (
                  <b style={{ color: "green" }}>
                    {fileData.structure.probablyWrongSugarPuckersCategory}
                  </b>
                )}
                {fileData.structure.probablyWrongSugarPuckersCategory ===
                  "Warning" && (
                  <b style={{ color: "orange" }}>
                    {fileData.structure.probablyWrongSugarPuckersCategory}
                  </b>
                )}
                {fileData.structure.probablyWrongSugarPuckersCategory ===
                  "Bad" && (
                  <b style={{ color: "red" }}>
                    {fileData.structure.probablyWrongSugarPuckersCategory}
                  </b>
                )}
                )
              </div>
              <div>
                <b>Rank: </b> {fileData.structure.pctRank}% ( category:{" "}
                {fileData.structure.rankCategory === "Good" && (
                  <b style={{ color: "green" }}>
                    {fileData.structure.rankCategory}
                  </b>
                )}
                {fileData.structure.rankCategory === "Warning" && (
                  <b style={{ color: "orange" }}>
                    {fileData.structure.rankCategory}
                  </b>
                )}
                {fileData.structure.rankCategory === "Bad" && (
                  <b style={{ color: "red" }}>
                    {fileData.structure.rankCategory}
                  </b>
                )}
                )
              </div>
            </div>
          ),
        })
      );

      return (
        <Row justify={"center"}>
          <Col span={20}>
            <Card
              title={"Overview of input parameters and constraints"}
              style={{ marginBottom: "24px" }}
            >
              <Descriptions bordered column={1}>
                <Descriptions.Item label="Model quality filter">
                  {molProbityOptions.find(
                      (option) =>
                          option.value === response.userRequest?.molProbityFilter
                  )?.label || "Unknown"}
                </Descriptions.Item>
                <Descriptions.Item label="2D structure constraints">
                  {response.userRequest.dotBracket ? (
                    <pre
                      style={{
                        whiteSpace: "pre-wrap",
                        wordBreak: "break-word",
                        margin: 0, // Ensure pre tag doesn't add extra margin
                      }}
                    >
                      {response.userRequest.dotBracket}
                    </pre>
                  ) : (
                    "Not provided"
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="Base pair analyzer">
                  {analyzerOptions.find(
                      (option) => option.value === response.userRequest?.analyzer
                  )?.label || "Unknown"}
                </Descriptions.Item>
                <Descriptions.Item label="Consensus weighting">
                  {response.userRequest?.confidenceLevel != null
                    ? `Confidence level: ${response.userRequest.confidenceLevel}`
                    : "Conditionally weighted consensus"}
                </Descriptions.Item>
              </Descriptions>
            </Card>
            <Card
              title={"Consensus 2D structure"}
              style={{ marginBottom: "24px" }}
            >
              <Collapse items={consensusDetails} />
            </Card>

            <Card
              title={"Model ranking by similarity to the consensus"}
              style={{ marginBottom: "24px" }}
            >
              <Table
                dataSource={rankingRows}
                columns={rankingColumns}
                scroll={{ x: "max-content" }}
              />
              <DownloadButton
                dataSource={rankingRows}
                columns={rankingColumns}
                fileName={`ranking.txt`}
              />
            </Card>

            <Card
              title={"Model-specific 2D structure analysis results"}
              style={{ marginBottom: "24px" }}
            >
              <Tabs items={perFileDetails} tabPosition={"left"} />
            </Card>

            {response.molProbity &&
              Object.keys(response.molProbity).length > 0 && (
                <Card
                  title={"Model quality filter results:"}
                  style={{ marginBottom: "24px" }}
                >
                  <Tabs items={molProbityDetails} tabPosition={"left"} />
                </Card>
              )}

            {removalReasons &&
              Object.keys(removalReasons).length > 0 &&
              (() => {
                const [columns, rows] = handleRemovalReasons();
                return (
                  <Card
                    title={"Removed files"}
                    style={{ marginBottom: "24px" }}
                  >
                    <Table
                      dataSource={rows}
                      columns={columns}
                      scroll={{ x: "max-content" }}
                    />
                  </Card>
                );
              })()}
          </Col>
        </Row>
      );
    }

    // Default view with the send button
    return (
      <Row justify={"center"}>
        <Col span={20}>
          <div style={{ marginBottom: "24px", textAlign: "justify" }}>
            {/* <p>RNAtive is a consensus-based RNA structure analysis system that combines multiple structural models to identify 
              reliable base pairs and stacking interactions. Upload your RNA 3D structure models in PDB or mmCIF format, and 
              RNAtive will analyze them using state-of-the-art base pair annotation tools. The system generates a consensus 
              structure by comparing annotations across all models, providing a reliable representation of the RNA's secondary 
              structure and tertiary interactions.</p> */}
            <p style={{ maxWidth: "600px", margin: "0 auto" }}>
              RNAtive is a consensus-based RNA structure analysis system{" "}
              designed to process multiple structural models sharing the same{" "}
              sequence and to identify reliable base pairs and stacking{" "}
              interactions. It supports model validation, improves structural{" "}
              predictions, and facilitates studies of RNA structure evolution.
              The tool accepts a minimum of two RNA 3D structure models in PDB{" "}
              or mmCIF format (with a total file size limit of 100 MB), and
              analyzes them using state-of-the-art base-pair annotation tools.
              Comparing annotations across all input models, it{" "}
              <b> generates a consensus structure </b>
              that highlights recurrent interactions, which are more likely to
              reflect stable, native-like folds. It then{" "}
              <b>evaluates and ranks the input models</b> based on their
              consistency with the derived consensus.
              {/* RNAtive is a consensus-based RNA structure analysis system
              designed to process multiple structural models to identify
              reliable base pairs and stacking interactions. Tailored for RNA
              structural biologists and bioinformaticians, it aids in validating
              RNA models, improving structural predictions, and studying the
              evolution of RNA structures. The tool accepts a minimum of two RNA
              3D structure models in PDB or mmCIF format, analyzes them using
              state-of-the-art base pair annotation tools, and generates a
              consensus structure by comparing annotations across all input
              models. The combined size of all PDB/mmCIF files cannot exceed
              100MB. Additionally, it ranks the input models based on their
              consistency with the derived consensus. */}
            </p>
          </div>

          <Form labelCol={{ span: 6 }} wrapperCol={{ span: 14 }}>
            <Form.Item
              label={
                <span>
                  <div
                    style={{
                      display: "inline-block",
                      whiteSpace: "normal",
                      maxWidth: "100%",
                      lineHeight: "1.7",
                      paddingBottom: "6px",
                    }}
                  ></div>
                  Example datasets{" "}
                  <Tooltip title="Click the button to load a demo dataset for testing and exploration.">
                    <QuestionCircleOutlined
                      style={{ position: "relative", zIndex: 999 }}
                    />
                  </Tooltip>
                </span>
              }
            >
              <Row gutter={8}>
                <Col>
                  <Button onClick={loadRNAPuzzlesExample}>
                    <Tooltip
                      title={
                        "13 models of the regulatory RNA motif from thymidylate synthase mRNA (3MEI), predicted in PZ01."
                      }
                    >
                      RNA-Puzzles models
                    </Tooltip>
                  </Button>
                </Col>
                <Col>
                  <Button onClick={loadDecoyExamples}>
                    <Tooltip
                      title={
                        "9 decoy models of the U2 small nuclear RNA fragment (1A9N)."
                      }
                    >
                      U2 snRNA decoys
                    </Tooltip>
                  </Button>
                </Col>
                <Col>
                  <Button onClick={loadmiRNAExample}>
                    <Tooltip
                      title={
                        "10 models of miRNA miR-663 from Rfam (RF00957), predicted by RNAComposer based on the CentroidFold-generated 2D structure."
                      }
                    >
                      miRNA mir-663
                    </Tooltip>
                  </Button>
                </Col>
              </Row>
            </Form.Item>
            <Form.Item
              label={
                <span>
                  {" "}
                  <b>NOTE</b>{" "}
                </span>
              }
            >
              <span>
                {" "}
                All uploaded RNA 3D structures must share the{" "}
                <b>exact same nucleotide sequence</b>.
              </span>
            </Form.Item>
            <Form.Item
              label={
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    RNA 3D models{" "}
                    <Tooltip title="Upload at least two RNA structure files in PDB or mmCIF format. You can also upload .zip, .tar.gz, or .tgz archives. Keep total file size under 100 MB">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
              }
            >
              <Row gutter={8}>
                <Col>
                  {!isSequenceOk ? (
                    <div style={{ color: "red" }}>
                      Please ensure that every file provided has the same
                      sequence
                    </div>
                  ) : (
                    <div></div>
                  )}
                  <Upload
                    accept={".pdb,.cif,.zip,.tar.gz,.tgz"}
                    multiple={true}
                    beforeUpload={beforeUpload}
                    fileList={fileList}
                    onChange={handleFileListChange}
                    onDownload={handleDownload}
                    showUploadList={{
                      showDownloadIcon: true,
                      downloadIcon: "Download",
                      showRemoveIcon: true,
                    }}
                    // We don't need customRequest anymore as we're handling the file status directly
                  >
                    <Button icon={<UploadOutlined />}>Upload</Button>
                  </Upload>
                  {fileList.length > 0 && (
                    <Button onClick={resetFileList}>
                      <Tooltip title={"Remove all uploaded files."}>
                        Reset
                      </Tooltip>
                    </Button>
                  )}
                </Col>
              </Row>
            </Form.Item>

            <Form.Item
              label={
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    Model quality filter{" "}
                    <Tooltip title="Filter input models based on structural quality assessed by MolProbity. Choose ‘No filtering’ to include all models, ‘Clashscore only’ to exclude models with poor clash scores, or ‘Strict’ to accept only models with good scores for clashes, bonds, and angles.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
              }
            >
              <Select
                options={molProbityOptions}
                value={molProbityFilter} // Use value prop for controlled component
                onChange={setMolProbityFilter}
              />
            </Form.Item>

            <Form.Item
              label={
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    2D structure constraints{" "}
                    <Tooltip title="Optionally, provide structural constraints using dot-bracket notation. Use brackets (e.g., '()' or '[]') to enforce base pairs, 'x' to enforce unpaired nucleotides, and '.' for positions without constraints. The consensus will follow these user-defined constraints.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
              }
            >
              {!isDotBracketOk ? (
                <div style={{ color: "red" }}>
                  Please ensure that dot-bracket's sequence is the same as in
                  the files provided.
                </div>
              ) : (
                <div></div>
              )}
              {seqLength !== brackLength ? (
                <div style={{ color: "red" }}>
                  Please ensure that the sequence is of the same length as the
                  structure.<br></br>
                  sequence: {seqLength} characters <br></br>
                  structure: {brackLength} characters <br></br>
                </div>
              ) : (
                <div></div>
              )}
              {opBracketsNum !== clBracketsNum ? (
                <div style={{ color: "red" }}>
                  Please ensure that the structure has the same number of
                  opening and closing brackets.<br></br>
                  opening brackets: {opBracketsNum} <br></br>
                  closing brackets: {clBracketsNum} <br></br>
                </div>
              ) : (
                <div></div>
              )}
              <div style={{ position: "relative" }}>
                <TextArea
                  rows={6}
                  variant="filled"
                  placeholder="Optional"
                  value={dotBracket}
                  onChange={handleDotBracket}
                  style={{ fontFamily: "monospace", paddingRight: "30px" }} // Make space for the "x"
                />
                {dotBracket && (
                  <CloseOutlined
                    onClick={resetDotBracket}
                    style={{
                      position: "absolute",
                      top: 8,
                      right: 8,
                      fontSize: "16px",
                      color: "#000",
                      cursor: "pointer",
                      zIndex: 1000,
                    }}
                  />
                )}
              </div>
            </Form.Item>

            <Form.Item
              label={
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    Base pair analyzer{" "}
                    <Tooltip title="Pick a tool to annotate nucleotide interactions from input RNA 3D models.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
              }
            >
              <Select
                options={analyzerOptions}
                defaultValue={analyzerOptions[0]}
                onChange={setAnalyzer}
              />
            </Form.Item>

            <Form.Item
              label={
                <div
                  style={{
                    display: "inline-block",
                    whiteSpace: "normal",
                    maxWidth: "100%",
                    lineHeight: "1.5",
                    paddingBottom: "4px",
                  }}
                >
                  <span>
                    Conditionally weighted consensus{" "}
                    {/* Frequency-based scoring{" "} */}
                    <Tooltip title="Switch on to rank models based on how often each interaction appears across the input set. Switch off to consider only high-confidence interactions above the threshold.">
                      <QuestionCircleOutlined
                        style={{ position: "relative", zIndex: 999 }}
                      />
                    </Tooltip>
                  </span>
                </div>
              }
            >
              <Switch checked={isFuzzy} onChange={setIsFuzzy} />
            </Form.Item>

            {!isFuzzy && (
              <Form.Item
                label={
                  <div
                    style={{
                      display: "inline-block",
                      whiteSpace: "normal",
                      maxWidth: "100%",
                      lineHeight: "1.5",
                      paddingBottom: "4px",
                    }}
                  >
                    <span style={{ opacity: fileList.length < 2 ? 0.5 : 1 }}>
                      Confidence level{" "}
                      <Tooltip title="Set the minimum number (percentage) of models in which an interaction must appear to be included in the consensus 2D structure. The corresponding percentage is displayed based on the total number of uploaded models. For example, a threshold of 5 models out of 10, corresponding to 50%, means the interaction is added to the consensus only if it occurs in at least half of the models.">
                        <QuestionCircleOutlined
                          style={{ position: "relative", zIndex: 999 }}
                        />
                      </Tooltip>
                    </span>
                  </div>
                }
              >
                <Row gutter={8} style={{ display: "flex" }}>
                  <Col flex={"auto"}>
                    <Slider
                      min={2}
                      max={fileList.length} // Ensure max updates dynamically
                      onChange={handleSliderChange}
                      value={confidenceLevel}
                      disabled={fileList.length < 2} // Gray out if fileList.length < 2
                    />
                  </Col>
                  <Col flex={"none"}>
                    <InputNumber
                      min={2}
                      max={fileList.length}
                      value={confidenceLevel}
                      onChange={handleSliderChange}
                      disabled={fileList.length < 2} // Gray out if fileList.length < 2
                      status=""
                      style={{
                        color: fileList.length < 2 ? "gray" : "inherit",
                      }}
                    />
                  </Col>

                  {fileList.length >= 2 && (
                    <span
                      style={{
                        opacity: fileList.length < 2 ? 0.5 : 1,
                        display: "inline-block",
                        minWidth: "4ch",
                        fontFamily: "monospace",
                      }}
                    >
                      {Math.round((confidenceLevel / fileList.length) * 100)}%
                    </span>
                  )}
                </Row>
              </Form.Item>
            )}

            <Form.Item wrapperCol={{ offset: 6 }}>
              {fileList.length < 2 ? (
                <Tooltip title="Upload at least 2 files">
                  <Button type="primary" disabled={true}>
                    Submit
                  </Button>
                </Tooltip>
              ) : !isDotBracketOk ||
                seqLength !== brackLength ||
                opBracketsNum !== clBracketsNum ? (
                <Tooltip title="Fix dot-bracket errors">
                  <Button type="primary" danger disabled>
                    Submit
                  </Button>
                </Tooltip>
              ) : !isSequenceOk ? (
                <Tooltip title="Please provide only files with the same sequence">
                  <Button type="primary" danger disabled>
                    Submit
                  </Button>
                </Tooltip>
              ) : (
                <Button type="primary" onClick={() => handleSendData()}>
                  Submit
                </Button>
              )}
            </Form.Item>
          </Form>
        </Col>
      </Row>
    );
  };
  return <div>{renderContent()}</div>;
}

export default Home;
