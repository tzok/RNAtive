import React, { useState, useEffect, useMemo } from "react";
import { Alert, Image, Spin } from "antd";

const SvgImg = ({ serverAddress, taskId, modelName }) => {
  const [svgContent, setSvgContent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const svgUrl = useMemo(() => {
    let url = `${serverAddress}/${taskId}/svg`;
    if (modelName) {
      url += `/${encodeURIComponent(modelName)}`;
    }
    return url;
  }, [serverAddress, taskId, modelName]);

  useEffect(() => {
    setLoading(true); // Reset loading state when props change
    setError(null); // Reset error state
    setSvgContent(null); // Reset content

    const fetchSvg = async () => {
      try {
        const response = await fetch(svgUrl, {
          method: "GET",
        });

        if (!response.ok) {
          throw new Error(`Failed to fetch SVG. Status: ${response.status}`);
        }

        const svgText = await response.text();
        setSvgContent(svgText);
        setLoading(false);
      } catch (err) {
        console.error("Error fetching SVG:", err.message);
        setError(err.message);
        setLoading(false);
      }
    };

    fetchSvg();
  }, [svgUrl]); // Depend on the memoized URL

  if (loading) {
    return <Spin />;
  }
  if (error) {
    return <Alert type={"error"} message={error.message} />;
  }

  // Create a blob for the SVG content to use for downloading
  const blob = new Blob([svgContent], { type: "image/svg+xml" });
  const blobUrl = URL.createObjectURL(blob);

  return <Image src={blobUrl} style={{ width: "60vh" }} />;
};

export default SvgImg;
