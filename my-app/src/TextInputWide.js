import React, { useRef, useEffect } from "react";
import "./TextInput.css";

const TextInputWide = ({ value = "", onTextChange }) => {
  const textareaRef = useRef(null);

  const handleInputChange = (e) => {
    onTextChange(e.target.value); // Notify the parent of the input change
  };

  // Automatically adjust the height of the textarea
  const adjustTextareaHeight = () => {
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.style.height = "auto"; // Reset height to measure full content
      textarea.style.height = `${Math.min(textarea.scrollHeight, 10 * parseFloat(getComputedStyle(textarea).lineHeight))}px`; // Max height for 10 lines
    }
  };

  useEffect(() => {
    adjustTextareaHeight(); // Adjust height on initial render and when value changes
  }, [value]);

  return (
    <div className="text-input-container">
      <textarea
        ref={textareaRef}
        value={value}
        onChange={handleInputChange}
        className="text-input"
        placeholder="Enter text here..."
        rows={1} // Initial rows
      />
    </div>
  );
};

export default TextInputWide;
