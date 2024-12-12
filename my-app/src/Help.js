import logo from "./logo.svg";
import "./App.css";
import Navbar from "./Navbar";

const Help = () => {
  return (
    <div>
      <header className="App-header">
        <div class="rounded-border">
          <p
            style={{
              fontSize: "30px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <b>HELP</b>
          </p>

          <p
            style={{
              fontSize: "25px",
            }}
          >
            <b>Table of contents:</b>
          </p>
          <div
            class="toc"
            style={{
              display: "flex",
              alignItems: "left",
              justifyContent: "left",
            }}
          >
            <ol>
              <li>
                <a href="#dolor1">What is RNAtive</a>
              </li>
              <li>
                <a href="#dolor2">How to use RNAtive</a>
              </li>
              <li>
                <a href="#dolor3">How to use RNAtive</a>
              </li>
              <li>
                <a href="#dolor4">Go to Paragraph 4</a>
                <ol>
                  <li>
                    <a href="#subdolor4-1">image</a>
                  </li>
                  <li>
                    <a href="#subdolor4-2">text2</a>
                  </li>
                </ol>
              </li>
              <li>
                <a href="#dolor5">Go to Paragraph 5</a>
              </li>
              <li>
                <a href="#dolor6">Go to Paragraph 6</a>
              </li>
            </ol>
          </div>
          <p
            id="dolor1"
            style={{
              fontSize: "25px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <b>What is RNAtive</b>
          </p>
          <p
            style={{
              fontSize: "18px",
            }}
          >
            RNAtive is a system that allows its users to rank a set of different
            models of the same RNA structure to determine which ones are the
            most realistic. RNAtive will return both the evaluation of
            individual pdb files provided, and its proposition of the most
            realistic actual looks of the molecule.
            <br></br>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <img src={logo} className="App-logo" alt="logo" />
            </div>
            <br></br>
            <br></br>
            <p
              id="dolor2"
              style={{
                fontSize: "25px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <b>How to use RNAtive</b>
            </p>
            Example mode
            <br></br>
            To test out the system without using any pdb files of your own, you
            can click the "Load Example" button on the top of the form at the
            home page. This will automatically load example pdb files to be send
            to the server. To send it for evaluation, press the "Send Data"
            button at the bottom of the form.
            <br></br>
            The "Preview Files" button will download those example pdb files to
            your computer, so you can evaluate their formatting or do other
            things with them.
            <br></br>
            User's pdb files
            <br></br>
            To evaluate your own pdb files, please drop them in the dropzone or
            click on the dropzone and select the .pdb files from your computer.
            <br></br>
            <br></br>
            <p
              id="dolor3"
              style={{
                fontSize: "25px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <b>Parameters</b>
            </p>
            There are multiple parameters to be selected
            <br></br>
            <br></br>
            <p
              id="dolor4"
              style={{
                fontSize: "25px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <b>Dolor 4</b>
            </p>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <img
                id="subdolor4-1"
                src={logo}
                className="App-logo"
                alt="logo"
              />
            </div>
            RNAtive is Vivamus vel facilisis ligula. In ac arcu ac ex fringilla
            tincidunt. Suspendisse vel feugiat sem. Etiam laoreet accumsan quam
            sodales volutpat. Vestibulum vestibulum nibh id massa tempor auctor.
            Phasellus et tortor fermentum, condimentum lacus quis, viverra nisl.
            <p id="subdolor4-2">
              Cras tristique venenatis leo quis blandit. In non interdum felis,
              eget ultrices eros. Pellentesque neque augue, molestie in quam
              facilisis, semper posuere leo. Donec mollis tortor non magna
              eleifend lacinia a aliquet massa. Maecenas egestas elementum
              massa, et sagittis massa maximus sodales. Nam condimentum
              condimentum dui.
            </p>
            <br></br>
            <br></br>
            <p
              id="dolor5"
              style={{
                fontSize: "25px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <b>Dolor 5</b>
            </p>
            Sed cursus quam ut est imperdiet, a molestie massa dignissim. Nunc
            commodo dictum dignissim. In dolor turpis, pharetra a malesuada eu,
            eleifend vitae ipsum. Quisque quam neque, congue ac ultricies vel,
            blandit id urna. Morbi tempor tempus venenatis. Aliquam erat
            volutpat. Vestibulum vitae ante eu massa efficitur sollicitudin ac
            vel velit. Nam sed diam eros. Sed vel cursus risus, ut laoreet
            felis.
            <br></br>
            <br></br>
            <p
              id="dolor6"
              style={{
                fontSize: "25px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <b>Dolor 6</b>
            </p>
            Praesent libero turpis, laoreet vel justo a, luctus fermentum dolor.
            Praesent pharetra odio non egestas sollicitudin. Sed sed elit porta,
            rutrum lorem eu, porttitor ligula. Interdum et malesuada fames ac
            ante ipsum primis in faucibus. Aenean ullamcorper arcu elit, a
            pulvinar tortor rhoncus eu. Aliquam neque enim, egestas sed
            porttitor pharetra, egestas in orci. Sed sed sodales ligula.
            <br></br>
            <br></br>
          </p>
        </div>
      </header>
    </div>
  );
};
export default Help;
