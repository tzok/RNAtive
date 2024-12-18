// SelectableList.js
import React from "react";
import "./SelectableList.css";

function SelectableList({ options, selectedOption, onSelect }) {
  return (
    <div className="selectable-list">
      {options.map((option) => (
        <div key={option} className={`list-item ${selectedOption === option ? "selected" : ""}`} onClick={() => onSelect(option)}>
          {option}
        </div>
      ))}
    </div>
  );
}

export default SelectableList;
