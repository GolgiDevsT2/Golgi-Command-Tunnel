namespace java io.golgi.gct.gen

//
// This Software (the “Software”) is supplied to you by Openmind Networks
// Limited ("Openmind") your use, installation, modification or
// redistribution of this Software constitutes acceptance of this disclaimer.
// If you do not agree with the terms of this disclaimer, please do not use,
// install, modify or redistribute this Software.
//
// TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED ON AN
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, EITHER
// EXPRESS OR IMPLIED INCLUDING, WITHOUT LIMITATION, ANY WARRANTIES OR
// CONDITIONS OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Each user of the Software is solely responsible for determining the
// appropriateness of using and distributing the Software and assumes all
// risks associated with use of the Software, including but not limited to
// the risks and costs of Software errors, compliance with applicable laws,
// damage to or loss of data, programs or equipment, and unavailability or
// interruption of operations.
//
// TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW OPENMIND SHALL NOT
// HAVE ANY LIABILITY FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
// EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, WITHOUT LIMITATION,
// LOST PROFITS, LOSS OF BUSINESS, LOSS OF USE, OR LOSS OF DATA), HOWSOEVER
// CAUSED UNDER ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OR DISTRIBUTION OF THE SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGES.
//

struct CommandDetails{
    1: required string cliKey,
    2: required string cmdLine,
}

struct FgetDetails{
    1: required string cliKey,
    2: required string filename,
}

struct FputDetails{
    1: required string cliKey,
    2: required string filename,
}

struct FputParams{
    1: required string svrKey,
}

exception FputException{
    1: required string cliKey,
    2: required i32 errCode,
    3: required string errText,
}

struct OutputPacket{
    1: required string dstKey,
    2: required i32 fd,
    3: required i32 pktNum,
    4: required data data,
}

struct TermStatus{
    1: required string key,
    2: required i32 pktTotal,
    3: required i32 exitCode,
    4: required string errorText,
}

service  GCT{
    void moreData(1:OutputPacket pkt),

    void launchCommand(1:CommandDetails cmdDetails),
    void commandComplete(1:TermStatus termStatus),

    void fget(1:FgetDetails fgetDetails),
    void fgetComplete(1:TermStatus termStatus),

    FputParams fputStart(1:FputDetails fputDetails)
		   throws(1:FputException fpe),
    TermStatus fputComplete(1:TermStatus termStatus),
}
