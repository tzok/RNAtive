import logo from "./logo.svg";
import "./App.css";
import Navbar from "./Navbar";

const Onas = () => {
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
            <b>About</b>
          </p>
          <p
            style={{
              fontSize:
                "18px" /* This font size is set using a 'string value' */,
            }}
          >
            RNAtive is Vivamus vel facilisis ligula. In ac arcu ac ex fringilla
            tincidunt. Suspendisse vel feugiat sem. Etiam laoreet accumsan quam
            sodales volutpat. Vestibulum vestibulum nibh id massa tempor auctor.
            Phasellus et tortor fermentum, condimentum lacus quis, viverra nisl.
            Cras tristique venenatis leo quis blandit. In non interdum felis,
            eget ultrices eros. Pellentesque neque augue, molestie in quam
            facilisis, semper posuere leo. Donec mollis tortor non magna
            eleifend lacinia a aliquet massa. Maecenas egestas elementum massa,
            et sagittis massa maximus sodales. Nam condimentum condimentum dui.
            <br></br>
            <br></br>
            <p
              style={{
                fontSize:
                  "25px" /* This font size is set using a 'string value' */,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <b>Authors</b>
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
              style={{
                fontSize:
                  "25px" /* This font size is set using a 'string value' */,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <b>Acknowledgements and Funding</b>
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
export default Onas;
