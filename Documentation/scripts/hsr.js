function hereDoc(f) {
  return f.toString().
      replace(/^[^\/]+\/\*!?/, '').
      replace(/\*\/[^\/]+$/, '');
}

document.write(hereDoc(function() {/*!
    <div id="header">
        <div class="wrap">
            <a href="site_index.html"><img src="images/Banner.png" style="display:block;"></a>
        </div>
        <div class="gsc-div">
            <gcse:search></gcse:search>
        </div>
    </div>
    <div id="sidebar">
        <iframe src="pages/toc.html" frameborder="0"></iframe>
    </div>
*/}));