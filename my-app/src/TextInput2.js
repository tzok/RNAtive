import React from "react";
import "./TextInput.css";

const TextInput = ({ value = "", onTextChange }) => {
  const handleInputChange = (e) => {
    onTextChange(e.target.value); // Notify the parent of the input change
  };

  return (
    <div className="text-input-container">
      <input
        type="text"
        value={value}
        onChange={handleInputChange}
        className="text-input"
        placeholder="Enter text here..."
      />
    </div>
  );
};

export default TextInput;
