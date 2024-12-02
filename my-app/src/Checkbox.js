import React from "react";
import "./Checkbox.css";

const Checkbox = ({ checked = false, onCheckChange }) => {
  const handleCheckboxChange = (e) => {
    onCheckChange(e.target.checked);
  };

  return (
    <div className="checkbox-container">
      <input
        type="checkbox"
        className="checkbox"
        checked={checked}
        onChange={handleCheckboxChange}
      />
    </div>
  );
};

export default Checkbox;
