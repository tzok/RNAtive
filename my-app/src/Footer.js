import React from "react";
import "./Navbar.css";
import { Link } from "react-router-dom";
import ImageSvg from "./RNAtive.svg";
import logo from "./PP_logotyp_ANG_WHITE.png";
const Footer = () => {
  return (
    <nav className="navbar">
      <div className="navbar-left">
        {/* <div style={{ width: "10%", textAlign: "center" }}>
          <img src={logo} alt="RNAtive" style={{ height: "32px" }} />
        </div> */}
        {/* <a href="/" className="logo">
          RNAtive
        </a> */}
      </div>
      <div className="navbar-center">
        {/* <div style={{ width: "10%", textAlign: "center" }}>
          <img src={logo} alt="PUT" style={{ height: "32px" }} />
        </div> */}

        <p>
          This website is free to use. We do not require any cookies or login to
          access it.
        </p>
        <img src={logo} alt="PUT" style={{ height: "64px" }} />
      </div>
      <div></div>
    </nav>
  );
};

export default Footer;
