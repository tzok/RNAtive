import logo from "./logo.svg";
import "./App.css";
import Navbar from "./Navbar";

const Cite = () => {
  return (
    <div>
      <header className="App-header">
        <div class="rounded-border">
          <p
            style={{
              fontSize:
                "25px" /* This font size is set using a 'string value' */,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <b>CITE US</b>
          </p>
          <p
            style={{
              fontSize:
                "18px" /* This font size is set using a 'string value' */,
            }}
          >
            <b>
              In any published work that has made use of RNAtive, please cite
              the following paper:
            </b>
            <br></br>
            <br></br>
            "GREAT PAPER ABOUT LOREM"<br></br>
            I. Ipsum, D. Dolor
            <br></br>
            <br></br>
            <b>Other related works include:</b>
            <br></br>
            <br></br>
            "Ighi kurumashu katarinuda magush ishi turbugha"<br></br>
            A. Shagrat, G. Drublak
            <br></br>
            <br></br>
            "Aghburz durbagu kirminudu bagur ishi darulu"<br></br>
            I. Ishi, D. Ashi
            <br></br>
            <br></br>
          </p>
        </div>
      </header>
    </div>
  );
};
export default Cite;
