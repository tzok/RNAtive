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
                <a href="#dolor3">Parameters</a>
                <ol>
                  <li>
                    <a href="#subdolor3-1">Consensus mode</a>
                  </li>
                  <li>
                    <a href="#subdolor3-2">Annotators</a>
                  </li>
                  <li>
                    <a href="#subdolor3-3">Visualizators</a>
                  </li>
                  <li>
                    <a href="#subdolor3-4">Confidence level</a>
                  </li>
                  <li>
                    <a href="#subdolor3-5">Dot-bracket parameter</a>
                  </li>
                </ol>
              </li>
              <li>
                <a href="#dolor4">Getting already submitted results</a>
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
            {/* <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <img src={logo} className="App-logo" alt="logo" />
            </div> */}
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
            <b>Example mode</b>
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
            <br></br>
            <b>Pdb files provided by user</b>
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
            <b id="subdolor3-1">Consensus mode</b>
            <br></br>
            There are four consensus modes available: canonical, non-canonical,
            stacking and all.
            <br></br>
            <br></br>
            <b id="subdolor3-2">Annotators</b>
            <br></br>
            There are six annotators available: MC-Annotate, BARNABA, RNAview,
            FR3D, BPnet, RNApolis
            <br></br>
            <br></br>
            <b id="subdolor3-3">Visualizators</b>
            <br></br>
            There are four visualizators available: VRNA, RNApuzzler,
            PseudoViewer and R-Chie
            <br></br>
            <br></br>
            <b id="subdolor3-4">Confidence level</b>
            <br></br>
            Confidence level tells how much is required by the RNAtive to label
            interaction as acceptable. Setting this value to 0 turns on the
            Fuzzy Mode which ...
            <br></br>
            <br></br>
            <b id="subdolor3-5">Dot-bracket parameter </b>
            <br></br>
            If desired, a dot-bracket form of the molecule can be given to the
            RNAtive to be treated as a reference for evaluation. Accepted format
            looks like this: <br></br>
            <i>
              ACGCCGCGCCAUGCCUGUGGCGG<br></br>
              (((((((((((((.(.(((((((<br></br>
              CCGCCGCGCCAUGCCUGUGGCGG<br></br>
              )))))))))))))..))))))))<br></br>
            </i>
            <br></br>
            Individual strands can be also represented with {">"}strand_name for
            example: <br></br>
            <i>
              {">"}strand_A<br></br>
              ACGCCGCGCCAUGCCUGUGGCGG<br></br>
              (((((((((((((.(.(((((((<br></br>
              {">"}strand_B<br></br>
              CCGCCGCGCCAUGCCUGUGGCGG<br></br>
              )))))))))))))..))))))))<br></br>
            </i>
            <p
              id="dolor4"
              style={{
                fontSize: "25px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <b>Getting already submitted results</b>
            </p>
            Once the data has been submitted, url of the site changes. The
            request id is added to it. To access answers from a different
            device, or to return to them later, one can simply copy that url and
            paste it later/elsewhere. There is also a "get already calculated
            results:" option at the bottom of the form. To access results from
            there, just paste the id (text after the "/" sign in the url). ID is
            also presented at the results page at "Your unique code:".
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
              <b>Results</b>
            </p>
            RNAtive returns the following:
            <ol>
              <li>Id code for this computation</li>
              <li>Visualization by the selected visualizator</li>
              <li>Cannonical pairs in consensus molecule</li>
              <li>Non cannonical pairs in consensus molecule</li>
              <li>Stackings for the consensus molecule</li>
              <li>Ranking of sent files</li>
              <li>Dot bracket of consensus molecule</li>
              <li>
                Results for each sent file:
                <ol>
                  <li>Canonical pairs</li>
                  <li>Non-canonical pairs</li>
                  <li>Stackings</li>
                  <li>Dot bracket</li>
                </ol>
              </li>
            </ol>
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
