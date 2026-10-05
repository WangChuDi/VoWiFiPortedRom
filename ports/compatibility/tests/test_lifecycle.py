"""Regression scenarios for framework evolution; no Android code is executed."""
from pathlib import Path
import runpy,unittest
C=runpy.run_path(str(Path(__file__).resolve().parents[1]/'check-lifecycle.py'))
P,A,F,S=C['PUBLIC'],C['ABSTRACT'],C['FINAL'],C['STATIC']
def method(name='hook',flags=P,descriptor='()V'):
    return {'name':name,'descriptor':descriptor,'access':flags}
def cls(parent,methods=(),flags=P):
    return {'access':flags,'super':parent,'interfaces':[],'methods':list(methods)}

class LifecycleTests(unittest.TestCase):
    def scan(self,base,app=(),middle=None):
        library={'android.Base':cls('java.lang.Object',base,P|A)}
        parent='android.Base'
        if middle is not None:
            library['android.Middle']=cls(parent,middle);parent='android.Middle'
        return C['analyze']({'app.Leaf':cls(parent,app)},library.get)
    def test_new_abstract_hook_is_detected(self):
        self.assertEqual(self.scan([method(flags=P|A)])['findings'][0]['problem'],'unimplemented-superclass-abstract')
    def test_intermediate_concrete_hook_satisfies_ancestor(self):
        self.assertEqual(self.scan([method(flags=P|A)],middle=[method()])['findings'],[])
    def test_app_implementation_satisfies_hook(self):
        self.assertEqual(self.scan([method(flags=P|A)],[method()])['findings'],[])
    def test_different_return_descriptor_does_not_satisfy_hook(self):
        self.assertTrue(self.scan([method(flags=P|A)],[method(descriptor='()I')])['findings'])
    def test_new_final_hook_on_distant_ancestor(self):
        self.assertEqual(self.scan([method(flags=P|F)],[method()],middle=[])['findings'][0]['problem'],'final-method-redeclared')
    def test_static_instance_collision(self):
        self.assertEqual(self.scan([method(flags=P|S)],[method()])['findings'][0]['problem'],'static-instance-collision')
    def test_private_method_is_not_inherited(self):
        self.assertEqual(self.scan([method(flags=C['PRIVATE']|F)],[method()])['findings'],[])
    def test_package_method_across_packages_is_not_override(self):
        self.assertEqual(self.scan([method(flags=F)],[method()])['findings'],[])
    def test_public_access_cannot_be_reduced(self):
        self.assertEqual(self.scan([method()],[method(flags=C['PROTECTED'])])['findings'][0]['problem'],'public-method-access-reduced')
    def test_missing_android_ancestor_is_reported(self):
        result=C['analyze']({'app.Leaf':cls('android.Missing')},lambda _:None)
        self.assertEqual(result['unresolved_framework_ancestors'],['android.Missing'])
    def test_core_boundary_is_explicit(self):
        self.assertEqual(self.scan([])['external_superclass_boundaries'],['java.lang.Object'])
    def test_return_to_declaring_package_cannot_override_final(self):
        library={'android.Base':cls('java.lang.Object',[method(flags=F)]),
                 'middle.Middle':cls('android.Base')}
        result=C['analyze']({'android.Leaf':cls('middle.Middle',[method(flags=0)])},library.get)
        self.assertEqual(result['findings'][0]['problem'],'final-method-redeclared')
    def test_hidden_package_abstract_still_requires_implementation(self):
        self.assertEqual(self.scan([method(flags=A)],middle=[])['findings'][0]['problem'],'unimplemented-superclass-abstract')
    def test_unrelated_public_method_does_not_implement_hidden_abstract(self):
        self.assertEqual(self.scan([method(flags=A)],[method()])['findings'][0]['problem'],'unimplemented-superclass-abstract')
    def test_same_package_public_bridge_satisfies_hidden_abstract(self):
        self.assertEqual(self.scan([method(flags=A)],[method()],middle=[method()])['findings'],[])

if __name__=='__main__':unittest.main()
